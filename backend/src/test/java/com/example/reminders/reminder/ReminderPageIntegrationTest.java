package com.example.reminders.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(ReminderPageIntegrationTest.SqlInspectionConfiguration.class)
class ReminderPageIntegrationTest {

    private static final String PAGE_URL = "/api/reminders/page";
    private static final LocalDate BROWSER_TODAY = LocalDate.of(2035, 4, 19);
    private static final Instant CREATED_AT = Instant.parse("2026-01-01T08:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderListRepository reminderListRepository;

    @Autowired
    private SqlCapture sqlCapture;

    private ReminderList defaultList;
    private ReminderList otherList;

    @BeforeEach
    void resetRemindersAndLists() {
        reminderRepository.deleteAll();
        reminderListRepository.deleteAll();
        defaultList = reminderListRepository.save(new ReminderList("Reminders"));
        otherList = reminderListRepository.save(new ReminderList("Work"));
        sqlCapture.clear();
    }

    @Test
    void defaultsToFirstPageOfTwentyWithoutChangingTheExistingArrayEndpoint() throws Exception {
        saveReminders(25);

        mockMvc.perform(get(PAGE_URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(20)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(25))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(25)));
    }

    @Test
    void returnsCustomMiddleAndPartialLastPagesWithAccurateMetadata() throws Exception {
        saveReminders(5);

        mockMvc.perform(get(PAGE_URL).param("page", "1").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].title").value("Reminder 2"))
                .andExpect(jsonPath("$.content[1].title").value("Reminder 1"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get(PAGE_URL).param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].title").value("Reminder 0"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void acceptsMaximumSizeAndMarksASinglePageAsFirstAndLast() throws Exception {
        saveReminders(1);

        mockMvc.perform(get(PAGE_URL).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void returnsAnEmptyPageForNoMatches() throws Exception {
        saveReminders(3);

        mockMvc.perform(get(PAGE_URL).param("q", "nothing matches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @ParameterizedTest
    @CsvSource({"4, 2, 3", "2147483647, 1, 5"})
    void returnsActualTotalsForAPageBeyondTheLastPage(int page, int size, int totalPages) throws Exception {
        saveReminders(5);

        mockMvc.perform(get(PAGE_URL).param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.page").value(page))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(totalPages))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(true));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            page, -1
            page, abc
            page, 1.5
            page, ''
            page, ' '
            page, 2147483648
            page, 9223372036854775808
            size, 0
            size, -1
            size, 101
            size, abc
            size, 1.5
            size, ''
            size, ' '
            size, 2147483648
            """)
    void rejectsInvalidPageOrSizeWithProblemDetail(String parameter, String value) throws Exception {
        assertBadRequest(get(PAGE_URL).param(parameter, value));
    }

    @Test
    void rejectsAnOffsetOutsideTheJpaIntegerRange() throws Exception {
        assertBadRequest(get(PAGE_URL).param("page", "2147483647").param("size", "2"));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            global,
            view, all
            view, today
            view, scheduled
            view, list
            """)
    void filtersBeforePagingAndCountsOnlyMatchesInEachScope(String scope, String view) throws Exception {
        saveViewFixtures();
        List<String> expected = switch (view == null ? "all" : view) {
            case "today" -> List.of("Spring today", "Today in Work");
            case "scheduled" -> List.of("Spring today", "Today in Work", "Spring later");
            case "list" -> List.of("Spring today", "Spring later");
            default -> List.of("Spring today", "Today in Work", "Spring later", "Unscheduled", "Spring in Work");
        };
        List<String> actual = new ArrayList<>();

        for (int page = 0; page < expected.size(); page++) {
            MockHttpServletRequestBuilder request = get(PAGE_URL)
                    .param("q", "\u2003\t sPrInG \n\u3000")
                    .param("scope", scope).param("page", Integer.toString(page)).param("size", "1");
            if (view != null) {
                request.param("view", view);
            }
            if ("today".equals(view)) {
                request.param("today", BROWSER_TODAY.toString());
            }
            if ("list".equals(view)) {
                request.param("listId", defaultList.getId().toString());
            }

            JsonNode response = readPage(request);
            assertThat(response.get("totalElements").asInt()).isEqualTo(expected.size());
            assertThat(response.get("totalPages").asInt()).isEqualTo(expected.size());
            assertThat(response.get("content").size()).isEqualTo(1);
            actual.add(response.get("content").get(0).get("title").asText());
        }

        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\n", "\u2003\u3000"})
    void keepsTheViewFilterForBlankQueries(String query) throws Exception {
        saveViewFixtures();

        mockMvc.perform(get(PAGE_URL).param("q", query)
                        .param("scope", "view").param("view", "list")
                        .param("listId", defaultList.getId().toString()).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].listId").value(defaultList.getId()))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\", "!", "\\%_!"})
    void treatsLikeMetacharactersAsLiteralsInBothContentAndCountQueries(String query) throws Exception {
        String matchingTitle = "batch" + query + "completed";
        saveReminder(matchingTitle, null, null, defaultList);
        saveReminder("Matching note", "batch" + query + "completed", null, otherList);
        saveReminder("batchxcompleted", "batch completed", null, defaultList);
        List<String> titles = new ArrayList<>();

        for (int page = 0; page < 2; page++) {
            JsonNode response = readPage(get(PAGE_URL).param("q", query)
                    .param("page", Integer.toString(page)).param("size", "1"));
            assertThat(response.get("totalElements").asInt()).isEqualTo(2);
            assertThat(response.get("totalPages").asInt()).isEqualTo(2);
            titles.add(response.get("content").get(0).get("title").asText());
        }

        assertThat(titles).containsExactlyInAnyOrder(matchingTitle, "Matching note");
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "\uD83D\uDE00"})
    void preservesNormalizedQueryLengthBoundaries(String character) throws Exception {
        String query = character.repeat(200 / character.length());
        saveReminder("Matching note", "Start " + query + " end", null, defaultList);

        mockMvc.perform(get(PAGE_URL).param("q", "\u2003\t" + query + "\n "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        assertBadRequest(get(PAGE_URL).param("q", query + "x"));
    }

    @Test
    void rejectsQueriesContainingNul() throws Exception {
        assertBadRequest(get(PAGE_URL).param("q", "spring\0boot"));
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            invalid, , ,
            '', , ,
            global, all, ,
            global, , 1,
            global, , , 2035-04-19
            , all, ,
            view, , ,
            view, unknown, ,
            view, all, 1,
            view, scheduled, , 2035-04-19
            view, list, ,
            view, list, '',
            view, list, abc,
            view, list, 9223372036854775808,
            view, list, 1, 2035-04-19
            view, today, ,
            view, today, , ''
            view, today, , 2035-02-30
            view, today, , 04/19/2035
            view, today, , +10000000-01-01
            view, today, 1, 2035-04-19
            """)
    void preservesInvalidScopeAndViewValidation(String scope, String view, String listId, String today) throws Exception {
        MockHttpServletRequestBuilder request = get(PAGE_URL).param("q", "spring");
        if (scope != null) {
            request.param("scope", scope);
        }
        if (view != null) {
            request.param("view", view);
        }
        if (listId != null) {
            request.param("listId", listId);
        }
        if (today != null) {
            request.param("today", today);
        }
        assertBadRequest(request);
    }

    @Test
    void returnsNotFoundForAMissingListEvenWithoutMatches() throws Exception {
        mockMvc.perform(get(PAGE_URL).param("q", "spring").param("scope", "view")
                        .param("view", "list").param("listId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void returnsReminderDtosRatherThanEntityRelationships() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Review framework", BROWSER_TODAY,
                LocalTime.of(9, 30), ReminderPriority.HIGH, otherList, "Read Spring notes"));

        mockMvc.perform(get(PAGE_URL).param("q", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(reminder.getId()))
                .andExpect(jsonPath("$.content[0].title").value("Review framework"))
                .andExpect(jsonPath("$.content[0].notes").value("Read Spring notes"))
                .andExpect(jsonPath("$.content[0].completed").value(false))
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$.content[0].dueDate").value(BROWSER_TODAY.toString()))
                .andExpect(jsonPath("$.content[0].dueTime").value("09:30"))
                .andExpect(jsonPath("$.content[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.content[0].listId").value(otherList.getId()))
                .andExpect(jsonPath("$.content[0].list").doesNotExist());
    }

    @Test
    void appliesTheCompleteV4OrderBeforePagingAndUsesDescendingIdOnlyForFinalTies() throws Exception {
        LocalTime morning = LocalTime.of(9, 0);
        Instant newer = CREATED_AT.plusSeconds(1);
        // Deliberately insert out of order so sorting only within each database page cannot pass.
        Reminder completed = ordered("Completed", true, BROWSER_TODAY.minusDays(2), morning, ReminderPriority.HIGH, newer);
        Reminder unscheduled = ordered("Unscheduled", false, null, null, ReminderPriority.HIGH, newer);
        Reminder laterDate = ordered("Later date", false, BROWSER_TODAY.plusDays(1), morning, ReminderPriority.HIGH, newer);
        Reminder dateOnly = ordered("Date only", false, BROWSER_TODAY, null, ReminderPriority.HIGH, newer);
        Reminder laterTime = ordered("Later time", false, BROWSER_TODAY, LocalTime.of(14, 0), ReminderPriority.HIGH, newer);
        Reminder none = ordered("None", false, BROWSER_TODAY, morning, ReminderPriority.NONE, newer);
        Reminder low = ordered("Low", false, BROWSER_TODAY, morning, ReminderPriority.LOW, newer);
        Reminder medium = ordered("Medium", false, BROWSER_TODAY, morning, ReminderPriority.MEDIUM, newer);
        Reminder newHigh = ordered("Newer high", false, BROWSER_TODAY, morning, ReminderPriority.HIGH, newer);
        Reminder oldHigh = ordered("Older high", false, BROWSER_TODAY, morning, ReminderPriority.HIGH, CREATED_AT);
        Reminder earlierLow = ordered("Earlier low", false, BROWSER_TODAY.minusDays(1), morning, ReminderPriority.LOW, CREATED_AT);
        // Newer high has a lower ID than Older high, proving createdAt outranks ID.
        Reminder tiedHigh = ordered("Tied high with larger ID", false, BROWSER_TODAY, morning, ReminderPriority.HIGH, newer);
        saveReminder("Not a match", null, BROWSER_TODAY.minusDays(3), defaultList);

        List<Long> expected = List.of(earlierLow.getId(), tiedHigh.getId(), newHigh.getId(), oldHigh.getId(),
                medium.getId(), low.getId(), none.getId(), laterTime.getId(), dateOnly.getId(),
                laterDate.getId(), unscheduled.getId(), completed.getId());
        List<Long> actual = new ArrayList<>();
        for (int page = 0; page < 4; page++) {
            JsonNode response = readPage(get(PAGE_URL).param("q", "Order fixture")
                    .param("page", Integer.toString(page)).param("size", "3"));
            assertThat(response.get("totalElements").asInt()).isEqualTo(expected.size());
            assertThat(response.get("totalPages").asInt()).isEqualTo(4);
            for (JsonNode item : response.get("content")) {
                actual.add(item.get("id").asLong());
            }
        }

        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }

    @Test
    void keepsStaticIdenticalTiesStableAcrossPageBoundaries() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            ids.add(ordered("Identical schedule", false, null, null, ReminderPriority.NONE, CREATED_AT).getId());
        }
        List<Long> expected = ids.reversed();
        List<Long> actual = new ArrayList<>();

        for (int page = 0; page < 4; page++) {
            JsonNode response = readPage(get(PAGE_URL).param("page", Integer.toString(page)).param("size", "2"));
            for (JsonNode item : response.get("content")) {
                actual.add(item.get("id").asLong());
            }
        }

        assertThat(actual).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
        JsonNode firstAgain = readPage(get(PAGE_URL).param("size", "2"));
        assertThat(firstAgain.get("content").get(0).get("id").asLong()).isEqualTo(expected.get(0));
        assertThat(firstAgain.get("content").get(1).get("id").asLong()).isEqualTo(expected.get(1));
    }

    @Test
    void sendsOrderingAndPaginationToTheDatabaseAndFetchesListsWithoutNPlusOne() throws Exception {
        for (int index = 0; index < 7; index++) {
            ReminderList list = reminderListRepository.save(new ReminderList("Distinct list " + index));
            saveReminder("SQL fixture " + index, null, null, list);
        }
        sqlCapture.clear();

        mockMvc.perform(get(PAGE_URL).param("q", "SQL fixture").param("page", "1").param("size", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.totalElements").value(7));

        List<String> selects = sqlCapture.statements.stream()
                .map(sql -> sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim())
                .filter(sql -> sql.startsWith("select "))
                .toList();
        // A full middle page needs one limited content query and one count, even across distinct lists.
        assertThat(selects).hasSize(2);
        String countSql = selects.stream().filter(sql -> sql.startsWith("select count(")).findFirst().orElseThrow();
        String contentSql = selects.stream().filter(sql -> !sql.startsWith("select count(")).findFirst().orElseThrow();
        assertThat(contentSql).contains(" join reminder_lists ", " order by ", "case when", " offset ", " fetch first ");
        assertThat(countSql).doesNotContain(" join ", " order by ", " offset ", " fetch first ");
    }

    private JsonNode readPage(MockHttpServletRequestBuilder request) throws Exception {
        String json = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    private void saveReminders(int count) {
        for (int index = 0; index < count; index++) {
            ordered("Reminder " + index, false, null, null, ReminderPriority.NONE, CREATED_AT.plusSeconds(index));
        }
    }

    private void saveViewFixtures() {
        saveReminder("Spring today", null, BROWSER_TODAY, defaultList);
        saveReminder("Today in Work", "Spring notes", BROWSER_TODAY, otherList);
        saveReminder("Spring later", null, BROWSER_TODAY.plusDays(1), defaultList);
        saveReminder("Unscheduled", "Spring notes", null, otherList);
        saveReminder("Spring in Work", null, null, otherList);
        saveReminder("Buy groceries", null, BROWSER_TODAY, defaultList);
    }

    private void saveReminder(String title, String notes, LocalDate dueDate, ReminderList list) {
        reminderRepository.save(new Reminder(title, dueDate, null, ReminderPriority.NONE, list, notes));
    }

    private Reminder ordered(String title, boolean completed, LocalDate dueDate, LocalTime dueTime,
                             ReminderPriority priority, Instant createdAt) {
        Reminder reminder = new Reminder(title, dueDate, dueTime, priority, defaultList, "Order fixture");
        reminder.setCompleted(completed);
        ReflectionTestUtils.setField(reminder, "createdAt", createdAt);
        return reminderRepository.save(reminder);
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

        void clear() {
            statements.clear();
        }
    }
}
