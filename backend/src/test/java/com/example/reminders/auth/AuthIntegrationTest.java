package com.example.reminders.auth;

import com.example.reminders.reminder.ReminderListRepository;
import com.example.reminders.reminder.ReminderRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthIntegrationTest {

    private static final String AUTH = "/api/auth";
    private static final String EMAIL = "learner@example.com";
    private static final String PASSWORD = "test account passphrase";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderListRepository reminderListRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetUsers() {
        reminderRepository.deleteAll();
        reminderListRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void registersCanonicalEmailAndStoresOnlyABcryptHashWithoutSigningIn() throws Exception {
        CsrfSession csrf = csrf(null);
        MvcResult result = register(csrf, "\u2003LEARNER@EXAMPLE.COM  ", PASSWORD);

        JsonNode userResponse = json(result);
        assertThat(userResponse.get("id").asLong()).isPositive();
        assertThat(userResponse.get("email").asText()).isEqualTo(EMAIL);
        assertThat(userResponse.get("createdAt").asText()).isNotBlank();
        assertThat(userResponse.size()).isEqualTo(3);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD);

        String hash = userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("{bcrypt}").isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();

        mockMvc.perform(get(AUTH + "/me").session(csrf.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rejectsDuplicateNormalizedEmailAndDatabaseAlsoEnforcesUniqueness() throws Exception {
        CsrfSession csrf = csrf(null);
        register(csrf, EMAIL, PASSWORD);

        mockMvc.perform(credentials("/register", csrf, "  LEARNER@example.com  ", PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(userRepository.count()).isEqualTo(1);

        String hash = userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();
        assertThatThrownBy(() -> jdbcTemplate.update(
                "insert into users (email, password_hash, created_at) values (?, ?, CURRENT_TIMESTAMP)",
                EMAIL, hash
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "", "   "})
    void rejectsInvalidEmail(String email) throws Exception {
        mockMvc.perform(credentials("/register", csrf(null), email, PASSWORD))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.email").isNotEmpty());
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rejectsWeakAndOverlongPasswordsBeforeHashing() throws Exception {
        CsrfSession csrf = csrf(null);
        for (String password : new String[]{"too short", " ".repeat(12), "a".repeat(73), "é".repeat(37)}) {
            mockMvc.perform(credentials("/register", csrf, EMAIL, password))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void rejectsMalformedCredentialJsonWithoutEchoingOrLoggingInput(CapturedOutput output) throws Exception {
        CsrfSession csrf = csrf(null);
        String unquotedValue = "malformed_credential_fixture";
        String body = "{\"email\":\"learner@example.com\",\"password\":" + unquotedValue + "}";

        mockMvc.perform(post(AUTH + "/login").session(csrf.session())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Malformed authentication request."));
        assertThat(output.getAll().contains(unquotedValue)).isFalse();
    }

    @Test
    void acceptsTheBcryptUtf8ByteLimitWithoutTrimmingPasswords() throws Exception {
        CsrfSession csrf = csrf(null);
        String password = " " + "é".repeat(35) + " ";
        register(csrf, EMAIL, password);
        login(csrf, EMAIL, password);
    }

    @Test
    void loginRotatesTheSessionAndPersistsAuthenticationAcrossRequests() throws Exception {
        CsrfSession csrf = csrf(null);
        register(csrf, EMAIL, PASSWORD);
        String anonymousId = csrf.session().getId();

        MvcResult login = login(csrf, "\u2003LEARNER@EXAMPLE.COM ", PASSWORD);
        MockHttpSession authenticated = session(login);
        assertThat(authenticated.getId()).isNotEqualTo(anonymousId);
        assertThat(json(login).size()).isEqualTo(3);

        mockMvc.perform(get(AUTH + "/me").session(authenticated))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.id").value(json(login).get("id").asLong()))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
        mockMvc.perform(get(AUTH + "/me").session(authenticated))
                .andExpect(status().isOk());
        mockMvc.perform(get(AUTH + "/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void unknownEmailAndWrongPasswordReturnTheSameLoginError() throws Exception {
        CsrfSession csrf = csrf(null);
        register(csrf, EMAIL, PASSWORD);

        String wrongPassword = failedLogin(csrf, EMAIL, "wrong passphrase");
        String unknownEmail = failedLogin(csrf, "unknown@example.com", "wrong passphrase");
        assertThat(wrongPassword).isEqualTo(unknownEmail);
        mockMvc.perform(get(AUTH + "/me").session(csrf.session()))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/register", "/login"})
    void authWritesRequireAValidCsrfHeader(String endpoint) throws Exception {
        CsrfSession csrf = csrf(null);
        byte[] body = credentialBody(EMAIL, PASSWORD);
        mockMvc.perform(post(AUTH + endpoint).session(csrf.session())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
        mockMvc.perform(post(AUTH + endpoint).session(csrf.session())
                        .header(csrf.headerName(), "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void logoutRequiresAuthenticationAndFreshCsrfAndInvalidatesTheSession() throws Exception {
        CsrfSession anonymous = csrf(null);
        mockMvc.perform(post(AUTH + "/logout").session(anonymous.session())
                        .header(anonymous.headerName(), anonymous.token()))
                .andExpect(status().isUnauthorized());
        register(anonymous, EMAIL, PASSWORD);
        MockHttpSession authenticated = session(login(anonymous, EMAIL, PASSWORD));

        mockMvc.perform(post(AUTH + "/logout").session(authenticated))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(AUTH + "/logout").session(authenticated)
                        .header(anonymous.headerName(), anonymous.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(AUTH + "/me").session(authenticated))
                .andExpect(status().isOk());

        CsrfSession fresh = csrf(authenticated);
        mockMvc.perform(post(AUTH + "/logout").session(authenticated)
                        .header(fresh.headerName(), fresh.token()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(content().string(""));
        assertThat(authenticated.isInvalid()).isTrue();
        mockMvc.perform(get(AUTH + "/me")).andExpect(status().isUnauthorized());

        CsrfSession afterLogout = csrf(null);
        mockMvc.perform(post(AUTH + "/login").session(afterLogout.session())
                        .header(fresh.headerName(), fresh.token())
                        .contentType(MediaType.APPLICATION_JSON).content(credentialBody(EMAIL, PASSWORD)))
                .andExpect(status().isForbidden());
        login(afterLogout, EMAIL, PASSWORD);
    }

    @Test
    void requiresASessionToReadReminderAndListRoutes() throws Exception {
        CsrfSession anonymous = csrf(null);
        String[] reads = {
                "/api/reminders", "/api/reminders/page",
                "/api/reminders/counts?today=2035-04-19", "/api/lists"
        };
        for (String path : reads) {
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.status").value(401));
        }

        register(anonymous, EMAIL, PASSWORD);
        CsrfSession authenticated = csrf(session(login(anonymous, EMAIL, PASSWORD)));
        for (String path : reads) {
            mockMvc.perform(get(path).session(authenticated.session()))
                    .andExpect(status().isOk());
        }
    }

    private MvcResult register(CsrfSession csrf, String email, String password) throws Exception {
        return mockMvc.perform(credentials("/register", csrf, email, password))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
    }

    private MvcResult login(CsrfSession csrf, String email, String password) throws Exception {
        return mockMvc.perform(credentials("/login", csrf, email, password)
                        .with(request -> {
                            request.setRequestedSessionId(csrf.session().getId());
                            request.setRequestedSessionIdValid(true);
                            request.setRequestedSessionIdFromCookie(true);
                            return request;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();
    }

    private String failedLogin(CsrfSession csrf, String email, String password) throws Exception {
        return mockMvc.perform(credentials("/login", csrf, email, password))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."))
                .andReturn().getResponse().getContentAsString();
    }

    private CsrfSession csrf(MockHttpSession session) throws Exception {
        MockHttpServletRequestBuilder request = get(AUTH + "/csrf");
        if (session != null) {
            request.session(session);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        JsonNode body = json(result);
        return new CsrfSession(session(result), body.get("headerName").asText(), body.get("token").asText());
    }

    private MockHttpServletRequestBuilder credentials(
            String endpoint, CsrfSession csrf, String email, String password
    ) throws Exception {
        return post(AUTH + endpoint).session(csrf.session())
                .header(csrf.headerName(), csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(credentialBody(email, password));
    }

    private byte[] credentialBody(String email, String password) throws Exception {
        return objectMapper.writeValueAsBytes(Map.of("email", email, "password", password));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private MockHttpSession session(MvcResult result) {
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(session).isNotNull();
        return session;
    }

    private record CsrfSession(MockHttpSession session, String headerName, String token) {
    }
}

