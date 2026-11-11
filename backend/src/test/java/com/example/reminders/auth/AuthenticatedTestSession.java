package com.example.reminders.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public record AuthenticatedTestSession(Long userId, MockHttpSession session, String csrfHeader, String csrfToken) {

    public static AuthenticatedTestSession registerAndLogin(MockMvc mvc, ObjectMapper mapper, String email)
            throws Exception {
        byte[] credentials = mapper.writeValueAsBytes(Map.of(
                "email", email, "password", "test-only reminder passphrase"
        ));
        MvcResult anonymousCsrf = mvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession anonymous = (MockHttpSession) anonymousCsrf.getRequest().getSession(false);
        JsonNode token = json(mapper, anonymousCsrf);

        mvc.perform(post("/api/auth/register").session(anonymous)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials))
                .andExpect(status().isCreated());
        MvcResult login = mvc.perform(post("/api/auth/login").session(anonymous)
                        .header(token.get("headerName").asText(), token.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(credentials)
                        .with(request -> {
                            request.setRequestedSessionId(anonymous.getId());
                            request.setRequestedSessionIdValid(true);
                            request.setRequestedSessionIdFromCookie(true);
                            return request;
                        }))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        JsonNode refreshed = json(mapper, mvc.perform(get("/api/auth/csrf").session(session))
                .andExpect(status().isOk()).andReturn());
        return new AuthenticatedTestSession(json(mapper, login).get("id").asLong(), session,
                refreshed.get("headerName").asText(), refreshed.get("token").asText());
    }

    public ResultActions perform(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.session(session).header(csrfHeader, csrfToken));
    }

    private static JsonNode json(ObjectMapper mapper, MvcResult result) throws Exception {
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    @Override
    public String toString() {
        return "AuthenticatedTestSession[userId=" + userId + "]";
    }
}

