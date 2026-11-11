package com.example.reminders.auth;

import com.example.reminders.reminder.Reminder;
import com.example.reminders.reminder.ReminderList;
import com.example.reminders.reminder.ReminderListRepository;
import com.example.reminders.reminder.ReminderPriority;
import com.example.reminders.reminder.ReminderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class ReminderOwnershipIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private UserRepository users;

    @Autowired
    private ReminderListRepository lists;

    @Autowired
    private ReminderRepository reminders;

    @Autowired
    private JdbcTemplate jdbc;

    private AuthenticatedTestSession alice;
    private AuthenticatedTestSession bob;

    @BeforeEach
    void registerSeparateAccounts() throws Exception {
        reminders.deleteAll();
        lists.deleteAll();
        users.deleteAll();
        alice = AuthenticatedTestSession.registerAndLogin(mvc, mapper, "alice@example.com");
        bob = AuthenticatedTestSession.registerAndLogin(mvc, mapper, "bob@example.com");
    }

    @Test
    void registersOneDistinctDefaultListPerAccount() throws Exception {
        Long aliceDefault = defaultList(alice).getId();
        Long bobDefault = defaultList(bob).getId();
        assertThat(aliceDefault).isNotEqualTo(bobDefault);

        for (var account : new AuthenticatedTestSession[]{alice, bob}) {
            account.perform(mvc, get("/api/lists"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(defaultList(account).getId()))
                    .andExpect(jsonPath("$[0].name").value("Reminders"))
                    .andExpect(jsonPath("$[0].isDefault").value(true));
        }
    }

    @Test
    void browserSuppliedOwnerIdCannotChooseTheOwner() throws Exception {
        Long listId = id(alice.perform(mvc, json(post("/api/lists"), Map.of(
                "name", "Alice work", "ownerId", bob.userId(), "isDefault", true
        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(false)));
        Long reminderId = id(alice.perform(mvc, json(post("/api/reminders"), Map.of(
                "title", "Alice reminder", "listId", listId, "ownerId", bob.userId()
        )))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listId").value(listId)));

        assertThat(lists.findByIdAndUser_Id(listId, alice.userId())).isPresent();
        assertThat(reminders.findByIdAndList_User_Id(reminderId, alice.userId())).isPresent();
        bob.perform(mvc, get("/api/lists")).andExpect(jsonPath("$", hasSize(1)));
        bob.perform(mvc, get("/api/reminders")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void scopesNormalReadsSearchPagesAndAggregateCountsToTheCurrentAccount() throws Exception {
        LocalDate today = LocalDate.now().plusDays(1);
        Long aliceDefault = defaultList(alice).getId();
        Long empty = createList(alice, "Empty");
        Long dated = createReminder(alice, "Alice dated", null, today, "shared notes");
        Long undated = createReminder(alice, "Shared Alice undated", null, null, null);
        Long bobItem = createReminder(bob, "Shared Bob", null, today, null);
        legacyReminder(today);

        alice.perform(mvc, get("/api/lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(aliceDefault))
                .andExpect(jsonPath("$[1].id").value(empty));
        alice.perform(mvc, get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(dated))
                .andExpect(jsonPath("$[1].id").value(undated));
        alice.perform(mvc, get("/api/reminders").param("q", "SHARED").param("scope", "global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(dated));
        bob.perform(mvc, get("/api/reminders").param("q", "shared"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(bobItem));

        // A full page forces a count query instead of inferring totals from a short page.
        alice.perform(mvc, get("/api/reminders/page").param("q", "shared").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(dated))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        alice.perform(mvc, get("/api/reminders/page").param("scope", "view")
                        .param("view", "all").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(undated))
                .andExpect(jsonPath("$.totalElements").value(2));
        alice.perform(mvc, get("/api/reminders/page").param("scope", "view").param("view", "today")
                        .param("today", today.toString()).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(dated))
                .andExpect(jsonPath("$.totalElements").value(1));

        String counts = mapper.writeValueAsString(Map.of(
                "all", 2, "today", 1, "scheduled", 1, "lists", Map.of(aliceDefault, 2, empty, 0)
        ));
        alice.perform(mvc, get("/api/reminders/counts").param("today", today.toString()))
                .andExpect(status().isOk())
                .andExpect(content().json(counts, JsonCompareMode.STRICT));
    }

    @Test
    void rejectsForeignAndLegacyListAccessLikeMissingLists() throws Exception {
        Reminder legacy = legacyReminder(null);
        Long foreign = defaultList(bob).getId();
        Long unowned = legacy.getList().getId();
        for (Long target : new Long[]{foreign, unowned, Long.MAX_VALUE}) {
            notFound(alice.perform(mvc, json(post("/api/reminders"), Map.of(
                    "title", "Should not be created", "listId", target
            ))));
            notFound(alice.perform(mvc, json(put("/api/lists/{id}", target), Map.of("name", "Changed"))));
            notFound(alice.perform(mvc, delete("/api/lists/{id}", target)));
            for (String path : new String[]{"/api/reminders", "/api/reminders/page"}) {
                notFound(alice.perform(mvc, get(path).param("scope", "view").param("view", "list")
                        .param("listId", target.toString())));
            }
        }

        assertThat(lists.findById(foreign).orElseThrow().getName()).isEqualTo("Reminders");
        assertThat(lists.findById(unowned).orElseThrow().getUser()).isNull();
        assertThat(reminders.findById(legacy.getId()).orElseThrow().getList().getId()).isEqualTo(unowned);
        assertThat(reminders.count()).isEqualTo(1);
    }

    @Test
    void rejectsForeignAndLegacyReminderWritesLikeMissingReminders() throws Exception {
        Long bobItem = createReminder(bob, "Bob unchanged", null, null, null);
        Long legacyId = legacyReminder(null).getId();
        for (Long target : new Long[]{bobItem, legacyId, Long.MAX_VALUE}) {
            notFound(alice.perform(mvc, json(put("/api/reminders/{id}", target), Map.of("title", "Changed"))));
            notFound(alice.perform(mvc, json(patch("/api/reminders/{id}/completion", target),
                    Map.of("completed", true))));
            notFound(alice.perform(mvc, delete("/api/reminders/{id}", target)));
        }

        bob.perform(mvc, get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Bob unchanged"))
                .andExpect(jsonPath("$[0].completed").value(false));
        assertThat(reminders.findById(legacyId).orElseThrow().getTitle()).isEqualTo("Shared legacy");
        assertThat(reminders.findById(legacyId).orElseThrow().isCompleted()).isFalse();
    }

    @Test
    void deletesAnOwnedListAndReassignsOnlyItsReminders() throws Exception {
        Long aliceList = createList(alice, "Work");
        Long bobList = createList(bob, "Work");
        Long aliceItem = createReminder(alice, "Alice item", aliceList, null, null);
        Long bobItem = createReminder(bob, "Bob item", bobList, null, null);

        alice.perform(mvc, delete("/api/lists/{id}", aliceList))
                .andExpect(status().isNoContent());

        assertThat(lists.findById(aliceList)).isEmpty();
        assertThat(lists.findById(bobList)).isPresent();
        assertThat(reminders.findById(aliceItem).orElseThrow().getList().getId())
                .isEqualTo(defaultList(alice).getId());
        assertThat(reminders.findById(bobItem).orElseThrow().getList().getId()).isEqualTo(bobList);
    }

    @Test
    void protectsEachDefaultByIdentityDespiteRenameAndDuplicateName() throws Exception {
        Long aliceDefault = defaultList(alice).getId();
        Long bobDefault = defaultList(bob).getId();
        Long sameName = createList(alice, "Reminders");
        alice.perform(mvc, json(put("/api/lists/{id}", aliceDefault), Map.of("name", "Personal")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isDefault").value(true));

        Long newReminder = createReminder(alice, "Goes into Personal", null, null, null);
        assertThat(reminders.findById(newReminder).orElseThrow().getList().getId()).isEqualTo(aliceDefault);
        alice.perform(mvc, delete("/api/lists/{id}", sameName)).andExpect(status().isNoContent());
        alice.perform(mvc, delete("/api/lists/{id}", aliceDefault)).andExpect(status().isBadRequest());
        bob.perform(mvc, delete("/api/lists/{id}", bobDefault)).andExpect(status().isBadRequest());
        notFound(alice.perform(mvc, delete("/api/lists/{id}", bobDefault)));
    }

    @Test
    void requiresAuthenticationAndValidCsrfBeforeBusinessWrites() throws Exception {
        for (var request : new MockHttpServletRequestBuilder[]{
                get("/api/reminders"), get("/api/lists"),
                json(post("/api/reminders"), Map.of("title", "Anonymous")),
                json(post("/api/lists"), Map.of("name", "Anonymous")),
                delete("/api/lists/{id}", defaultList(alice).getId())
        }) {
            mvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        mvc.perform(json(post("/api/reminders"), Map.of("title", "Missing CSRF")).session(alice.session()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mvc.perform(json(post("/api/lists"), Map.of("name", "Invalid CSRF")).session(alice.session())
                        .header(alice.csrfHeader(), "invalid"))
                .andExpect(status().isForbidden());
        assertThat(reminders.count()).isZero();
        assertThat(lists.count()).isEqualTo(2);
    }

    private ReminderList defaultList(AuthenticatedTestSession account) {
        return lists.findByUser_IdAndDefaultListTrue(account.userId()).orElseThrow();
    }

    private Long createList(AuthenticatedTestSession account, String name) throws Exception {
        return id(account.perform(mvc, json(post("/api/lists"), Map.of("name", name)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isDefault").value(false)));
    }

    private Long createReminder(
            AuthenticatedTestSession account, String title, Long listId, LocalDate dueDate, String notes
    ) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("listId", listId);
        body.put("dueDate", dueDate);
        body.put("notes", notes);
        return id(account.perform(mvc, json(post("/api/reminders"), body)).andExpect(status().isCreated()));
    }

    private Reminder legacyReminder(LocalDate date) {
        jdbc.update("insert into reminder_lists (name, created_at, is_default) values (?, CURRENT_TIMESTAMP, false)",
                "Legacy list");
        Long id = jdbc.queryForObject("select id from reminder_lists where name = ? and user_id is null",
                Long.class, "Legacy list");
        ReminderList list = lists.findById(id).orElseThrow();
        return reminders.save(new Reminder("Shared legacy", date, null, ReminderPriority.NONE, list));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, Map<String, ?> body)
            throws Exception {
        return request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
    }

    private Long id(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private void notFound(ResultActions result) throws Exception {
        result.andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }
}

