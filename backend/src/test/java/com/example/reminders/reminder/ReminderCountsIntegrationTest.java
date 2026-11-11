package com.example.reminders.reminder;

import com.example.reminders.auth.AppUser;
import com.example.reminders.auth.AuthenticatedTestSession;
import com.example.reminders.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReminderCountsIntegrationTest.SqlInspectionConfiguration.class)
class ReminderCountsIntegrationTest {

    private static final String COUNTS_URL = "/api/reminders/counts";
    private static final LocalDate BROWSER_TODAY = LocalDate.of(2035, 4, 19);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderListRepository reminderListRepository;

    @Autowired
    private UserRepository userRepository;

    private AuthenticatedTestSession session;
    private AppUser owner;

    @Autowired
    private SqlCapture sqlCapture;

    private ReminderList defaultList;

    @BeforeEach
    void resetDatabase() throws Exception {
        reminderRepository.deleteAllInBatch();
        reminderListRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        session = AuthenticatedTestSession.registerAndLogin(mockMvc, objectMapper, "regression@example.com");
        owner = userRepository.findById(session.userId()).orElseThrow();
        defaultList = reminderListRepository.findByUser_IdAndDefaultListTrue(session.userId()).orElseThrow();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return session.perform(mockMvc, request);
    }

    @Test
    void countsAllViewsInOneGroupedQueryIncludingCompletedReminders() throws Exception {
        ReminderList work = saveList("Work");
        ReminderList empty = saveList("Empty");
        saveReminder(defaultList, BROWSER_TODAY, true);
        saveReminder(defaultList, BROWSER_TODAY.minusDays(1), false);
        saveReminder(defaultList, null, true);
        saveReminder(work, BROWSER_TODAY, false);
        saveReminder(work, BROWSER_TODAY.plusDays(1), true);
        sqlCapture.statements.clear();

        assertCounts(BROWSER_TODAY, 5, 2, 4, Map.of(
                defaultList.getId(), 3L,
                work.getId(), 2L,
                empty.getId(), 0L
        ));

        List<String> selects = sqlCapture.statements.stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim())
                .filter(sql -> sql.startsWith("select "))
                .toList();
        assertThat(selects).hasSize(1);
        assertThat(selects.getFirst())
                .contains("count(", "sum(", "case when", " group by ")
                .doesNotContain(".title", ".notes", ".priority", ".created_at");
    }

    @Test
    void returnsZeroCountsAndAnEmptyListMapWhenTheOwnerHasNoLists() throws Exception {
        reminderListRepository.delete(defaultList);
        assertCounts(BROWSER_TODAY, 0, 0, 0, Map.of());
    }

    @Test
    void returnsExplicitZeroForEachListWhenThereAreNoReminders() throws Exception {
        ReminderList work = saveList("Work");

        assertCounts(BROWSER_TODAY, 0, 0, 0, Map.of(defaultList.getId(), 0L, work.getId(), 0L));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "2035-4-19", "2035-02-30", "2035-13-01", "2035-04-19T00:00:00", "19/04/2035"})
    void rejectsInvalidOrMissingTodayWithProblemDetail(String today) throws Exception {
        var request = get(COUNTS_URL);
        if (today != null) {
            request.param("today", today);
        }

        perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void reflectsListDeletionAndReassignmentWithoutChangingSmartCounts() throws Exception {
        ReminderList work = saveList("Work");
        saveReminder(defaultList, null, false);
        saveReminder(work, BROWSER_TODAY, true);
        saveReminder(work, BROWSER_TODAY.plusDays(1), false);

        assertCounts(BROWSER_TODAY, 3, 1, 2, Map.of(defaultList.getId(), 1L, work.getId(), 2L));

        perform(delete("/api/lists/{id}", work.getId()))
                .andExpect(status().isNoContent());

        assertCounts(BROWSER_TODAY, 3, 1, 2, Map.of(defaultList.getId(), 3L));
    }

    @Test
    void reflectsDueDateChangesInTodayAndScheduledCounts() throws Exception {
        LocalDate browserToday = LocalDate.now().plusDays(1);
        Reminder reminder = saveReminder(defaultList, null, false);
        Map<Long, Long> listCounts = Map.of(defaultList.getId(), 1L);

        assertCounts(browserToday, 1, 0, 0, listCounts);

        updateDueDate(reminder.getId(), browserToday);
        assertCounts(browserToday, 1, 1, 1, listCounts);

        updateDueDate(reminder.getId(), browserToday.plusDays(1));
        assertCounts(browserToday, 1, 0, 1, listCounts);

        updateDueDate(reminder.getId(), null);
        assertCounts(browserToday, 1, 0, 0, listCounts);
    }

    private void assertCounts(LocalDate date, long all, long today, long scheduled, Map<Long, Long> lists)
            throws Exception {
        String expected = objectMapper.writeValueAsString(Map.of(
                "all", all,
                "today", today,
                "scheduled", scheduled,
                "lists", lists
        ));
        perform(get(COUNTS_URL).param("today", date.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(expected, JsonCompareMode.STRICT));
    }

    private ReminderList saveList(String name) {
        return reminderListRepository.save(new ReminderList(name, owner));
    }

    private Reminder saveReminder(ReminderList list, LocalDate dueDate, boolean completed) {
        Reminder reminder = new Reminder("Count fixture", dueDate, null, ReminderPriority.NONE, list, null);
        reminder.setCompleted(completed);
        return reminderRepository.save(reminder);
    }

    private void updateDueDate(Long reminderId, LocalDate dueDate) throws Exception {
        perform(put("/api/reminders/{id}", reminderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Updated reminder","dueDate":%s}
                                """.formatted(objectMapper.writeValueAsString(dueDate))))
                .andExpect(status().isOk());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlInspectionConfiguration {
        @Bean
        SqlCapture sqlCapture() {
            return new SqlCapture();
        }

        @Bean
        HibernatePropertiesCustomizer inspectSql(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {
        private final List<String> statements = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            statements.add(sql);
            return sql;
        }
    }
}
