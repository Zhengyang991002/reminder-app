package com.example.reminders.reminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderSearchIntegrationTest {

    private static final LocalDate BROWSER_TODAY = LocalDate.of(2035, 4, 19);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderListRepository reminderListRepository;

    private ReminderList defaultList;
    private ReminderList otherList;

    @BeforeEach
    void resetRemindersAndLists() {
        reminderRepository.deleteAll();
        reminderListRepository.deleteAll();
        defaultList = reminderListRepository.save(new ReminderList("Reminders"));
        otherList = reminderListRepository.save(new ReminderList("Work"));
    }

    @Test
    void searchesTitleOrNotesByCaseInsensitiveSubstringAcrossLists() throws Exception {
        saveReminder("Prepare SPRING slides", null, null, defaultList);
        saveReminder("Review framework", "Read about Spring Boot\nTake notes", null, otherList);
        saveReminder("Clean desk", null, null, defaultList);

        mockMvc.perform(get("/api/reminders").param("q", "rInG").param("scope", "global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Prepare SPRING slides", "Review framework")));
    }

    @Test
    void stripsLeadingAndTrailingWhitespaceAndDefaultsToGlobal() throws Exception {
        saveReminder("Review Spring Boot", null, null, otherList);
        saveReminder("Review calendar", null, null, defaultList);

        mockMvc.perform(get("/api/reminders").param("q", "\u2003\t sPrInG \n\u3000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Review Spring Boot")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " \t\n", "\u2003\u3000"})
    void treatsBlankQueryAsNoTextFilter(String query) throws Exception {
        saveReminder("Read chapter", "Keep the examples", null, defaultList);
        saveReminder("Buy groceries", null, null, otherList);

        mockMvc.perform(get("/api/reminders").param("q", query).param("scope", "global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Read chapter", "Buy groceries")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "\uD83D\uDE00"})
    void acceptsTwoHundredCharactersAfterStrippingWhitespace(String character) throws Exception {
        String query = character.repeat(200 / character.length());
        saveReminder("Full note", "Start " + query + " end", null, defaultList);
        saveReminder("Short note", query.substring(0, 200 - character.length()), null, otherList);

        mockMvc.perform(get("/api/reminders").param("q", "\u2003\t" + query + "\n "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Full note")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x", "\uD83D\uDE00"})
    void rejectsQueriesLongerThanTwoHundredNormalizedJavaStringCharacters(String character) throws Exception {
        String query = character.repeat(200 / character.length()) + "x";

        assertBadRequest(get("/api/reminders").param("q", " \t" + query + "\u2003"));
    }

    @Test
    void rejectsQueriesContainingNul() throws Exception {
        assertBadRequest(get("/api/reminders").param("q", "spring\0boot"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\", "!", "\\%_!"})
    void treatsWildcardAndEscapeCharactersLiterally(String query) throws Exception {
        String matchingTitle = "batch" + query + "completed";
        saveReminder(matchingTitle, null, null, defaultList);
        saveReminder("Matching note", "batch" + query + "completed", null, otherList);
        saveReminder("batchxcompleted", "batch completed", null, defaultList);

        mockMvc.perform(get("/api/reminders").param("q", query))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder(matchingTitle, "Matching note")));
    }

    @Test
    void searchesAllRemindersInAllView() throws Exception {
        saveViewFixtures();

        mockMvc.perform(get("/api/reminders").param("q", "spring").param("scope", "view").param("view", "all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder(
                        "Spring today", "Today in Work", "Spring later", "Unscheduled", "Spring in Work")));
    }

    @Test
    void searchesOnlyTheBrowserDateInTodayView() throws Exception {
        saveViewFixtures();

        mockMvc.perform(get("/api/reminders")
                        .param("q", "spring").param("scope", "view").param("view", "today")
                        .param("today", BROWSER_TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Spring today", "Today in Work")));
    }

    @Test
    void searchesOnlyDatedRemindersInScheduledView() throws Exception {
        saveViewFixtures();

        mockMvc.perform(get("/api/reminders")
                        .param("q", "spring").param("scope", "view").param("view", "scheduled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Spring today", "Today in Work", "Spring later")));
    }

    @Test
    void searchesOnlyTheSpecifiedPersistedListInListView() throws Exception {
        saveViewFixtures();

        mockMvc.perform(get("/api/reminders")
                        .param("q", "spring").param("scope", "view").param("view", "list")
                        .param("listId", defaultList.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Spring today", "Spring later")));
    }

    @Test
    void appliesViewFilterWhenQueryIsBlank() throws Exception {
        saveViewFixtures();

        mockMvc.perform(get("/api/reminders")
                        .param("q", "  ").param("scope", "view").param("view", "list")
                        .param("listId", defaultList.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Spring today", "Spring later", "Buy groceries")));
    }

    @ParameterizedTest(name = "[{index}] scope={0}, view={1}, listId={2}, today={3}")
    @CsvSource(textBlock = """
            invalid, , ,
            '', , ,
            global, all, ,
            global, , 1,
            global, , , 2035-04-19
            , all, ,
            , , 1,
            , , , 2035-04-19
            view, , ,
            view, '', ,
            view, unknown, ,
            view, all, 1,
            view, all, , 2035-04-19
            view, scheduled, 1,
            view, scheduled, , 2035-04-19
            view, list, ,
            view, list, '',
            view, list, abc,
            view, list, 9223372036854775808,
            view, list, 9223372036854775807, 2035-04-19
            view, today, ,
            view, today, , ''
            view, today, , 2035-02-30
            view, today, , 04/19/2035
            view, today, , +10000000-01-01
            view, today, 1, 2035-04-19
            """)
    void rejectsInvalidOrConflictingCriteria(String scope, String view, String listId, String today) throws Exception {
        MockHttpServletRequestBuilder request = get("/api/reminders").param("q", "spring");
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
    void returnsNotFoundForAnUnknownList() throws Exception {
        mockMvc.perform(get("/api/reminders")
                        .param("q", "spring").param("scope", "view").param("view", "list")
                        .param("listId", Long.toString(Long.MAX_VALUE)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void preservesV4OrderingAmongMatches() throws Exception {
        Instant older = Instant.parse("2026-01-01T08:00:00Z");
        Instant newer = older.plusSeconds(1);
        LocalTime morning = LocalTime.of(9, 0);
        LocalDate earlier = BROWSER_TODAY.minusDays(1);

        saveOrderedMatch("Completed", true, earlier, morning, ReminderPriority.HIGH, newer);
        saveOrderedMatch("Unscheduled", false, null, null, ReminderPriority.HIGH, newer);
        saveOrderedMatch("Later date", false, BROWSER_TODAY.plusDays(1), morning, ReminderPriority.HIGH, newer);
        saveOrderedMatch("Date only", false, BROWSER_TODAY, null, ReminderPriority.HIGH, newer);
        saveOrderedMatch("Later time", false, BROWSER_TODAY, LocalTime.NOON, ReminderPriority.HIGH, newer);
        saveOrderedMatch("Low priority", false, BROWSER_TODAY, morning, ReminderPriority.LOW, newer);
        saveOrderedMatch("Older high priority", false, BROWSER_TODAY, morning, ReminderPriority.HIGH, older);
        saveOrderedMatch("Newer high priority", false, BROWSER_TODAY, morning, ReminderPriority.HIGH, newer);
        saveReminder("Other reminder", null, earlier, defaultList);

        mockMvc.perform(get("/api/reminders").param("q", "search fixture"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains(
                        "Newer high priority", "Older high priority", "Low priority", "Later time",
                        "Date only", "Later date", "Unscheduled", "Completed")));
    }

    @Test
    void preservesTheResponseAndIncludesAllRemindersWithoutParameters() throws Exception {
        Reminder scheduled = reminderRepository.save(new Reminder(
                "Review framework", BROWSER_TODAY, LocalTime.of(9, 30), ReminderPriority.HIGH,
                otherList, "Read about Spring Boot"));
        saveReminder("Buy groceries", null, null, defaultList);

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(scheduled.getId()))
                .andExpect(jsonPath("$[0].title").value("Review framework"))
                .andExpect(jsonPath("$[0].completed").value(false))
                .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
                .andExpect(jsonPath("$[0].dueDate").value(BROWSER_TODAY.toString()))
                .andExpect(jsonPath("$[0].dueTime").value("09:30"))
                .andExpect(jsonPath("$[0].priority").value("HIGH"))
                .andExpect(jsonPath("$[0].listId").value(otherList.getId()))
                .andExpect(jsonPath("$[0].notes").value("Read about Spring Boot"))
                .andExpect(jsonPath("$[1].title").value("Buy groceries"))
                .andExpect(jsonPath("$[1].notes").hasJsonPath())
                .andExpect(jsonPath("$[1].notes").value(nullValue()));
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").isNotEmpty());
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

    private void saveOrderedMatch(
            String title,
            boolean completed,
            LocalDate dueDate,
            LocalTime dueTime,
            ReminderPriority priority,
            Instant createdAt
    ) {
        Reminder reminder = new Reminder(title, dueDate, dueTime, priority, defaultList, "Search fixture");
        reminder.setCompleted(completed);
        ReflectionTestUtils.setField(reminder, "createdAt", createdAt);
        reminderRepository.save(reminder);
    }
}
