package com.example.reminders.reminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReminderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderListRepository reminderListRepository;

    private ReminderList defaultReminderList;

    @BeforeEach
    void clearReminders() {
        reminderRepository.deleteAll();
        reminderListRepository.deleteAll();
        defaultReminderList = reminderListRepository.save(new ReminderList("Reminders"));
    }

    @Test
    void createsAndListsReminders() throws Exception {
        String dueDate = LocalDate.now().plusDays(1).toString();
        String request = """
                {
                  "title": "  Buy groceries  ",
                  "dueDate": "%s"
                }
                """.formatted(dueDate);

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Buy groceries"))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.dueDate").value(dueDate))
                .andExpect(jsonPath("$.dueTime").isEmpty())
                .andExpect(jsonPath("$.listId").value(defaultReminderList.getId()));

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Buy groceries"))
                .andExpect(jsonPath("$[0].listId").value(defaultReminderList.getId()));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getList().getId()).isEqualTo(defaultReminderList.getId());
    }

    @Test
    void createsReminderWithoutDueDateOrDueTime() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No schedule\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").isEmpty())
                .andExpect(jsonPath("$.dueTime").isEmpty());

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getDueDate()).isNull();
        assertThat(createdReminder.getDueTime()).isNull();
    }

    @Test
    void defaultsOmittedPriorityToNoneWhenCreating() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No priority\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("NONE"));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getPriority()).isEqualTo(ReminderPriority.NONE);
    }

    @Test
    void createsReminderWithExplicitNonePriority() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No priority\",\"priority\":\"NONE\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("NONE"));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getPriority()).isEqualTo(ReminderPriority.NONE);
    }

    @Test
    void createsReminderWithLowPriority() throws Exception {
        createReminderWithPriority(ReminderPriority.LOW);
    }

    @Test
    void createsReminderWithMediumPriority() throws Exception {
        createReminderWithPriority(ReminderPriority.MEDIUM);
    }

    @Test
    void createsReminderWithHighPriority() throws Exception {
        createReminderWithPriority(ReminderPriority.HIGH);
    }

    @Test
    void rejectsInvalidPriorityWhenCreating() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Invalid priority\",\"priority\":\"URGENT\"}"))
                .andExpect(status().isBadRequest());

        assertThat(reminderRepository.count()).isZero();
    }

    @Test
    void createsReminderWithDueDateAndDueTime() throws Exception {
        String dueDate = LocalDate.now().plusDays(1).toString();
        String request = """
                {
                  "title": "Call dentist",
                  "dueDate": "%s",
                  "dueTime": "14:30"
                }
                """.formatted(dueDate);

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value(dueDate))
                .andExpect(jsonPath("$.dueTime").value("14:30"));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getDueTime()).isEqualTo(LocalTime.of(14, 30));
    }

    @Test
    void rejectsDueTimeWithoutDueDateWhenCreating() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Call dentist\",\"dueDate\":null,\"dueTime\":\"14:30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.dueTime").value("Due time requires a due date"));
    }

    @Test
    void listsReminderLists() throws Exception {
        reminderListRepository.save(new ReminderList("School"));

        mockMvc.perform(get("/api/lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", hasItems("Reminders", "School")));
    }

    @Test
    void createsAReminderList() throws Exception {
        mockMvc.perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  School  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("School"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void allowsDuplicateReminderListNames() throws Exception {
        String request = "{\"name\":\"School\"}";

        mockMvc.perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        assertThat(reminderListRepository.findAll())
                .extracting(ReminderList::getName)
                .containsExactlyInAnyOrder("Reminders", "School", "School");
    }

    @Test
    void rejectsBlankReminderListName() throws Exception {
        mockMvc.perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.name").value("Name must not be blank"));
    }

    @Test
    void rejectsReminderListNameLongerThan100Characters() throws Exception {
        String name = "a".repeat(101);

        mockMvc.perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.name").value("Name must be at most 100 characters"));
    }

    @Test
    void renamesAReminderList() throws Exception {
        ReminderList reminderList = reminderListRepository.save(new ReminderList("School"));

        mockMvc.perform(put("/api/lists/{id}", reminderList.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  CS5010  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reminderList.getId()))
                .andExpect(jsonPath("$.name").value("CS5010"));

        assertThat(reminderListRepository.findById(reminderList.getId()).orElseThrow().getName())
                .isEqualTo("CS5010");
    }

    @Test
    void returnsNotFoundWhenRenamingAMissingReminderList() throws Exception {
        mockMvc.perform(put("/api/lists/{id}", 9999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CS5010\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Reminder list with id 9999 was not found"));
    }

    @Test
    void deletesANormalReminderList() throws Exception {
        ReminderList reminderList = reminderListRepository.save(new ReminderList("School"));

        mockMvc.perform(delete("/api/lists/{id}", reminderList.getId()))
                .andExpect(status().isNoContent());

        assertThat(reminderListRepository.existsById(reminderList.getId())).isFalse();
    }

    @Test
    void movesRemindersToTheDefaultListBeforeDeletingAReminderList() throws Exception {
        ReminderList reminderList = reminderListRepository.save(new ReminderList("School"));
        Reminder reminder = reminderRepository.save(new Reminder("Study", null, null, reminderList));

        mockMvc.perform(delete("/api/lists/{id}", reminderList.getId()))
                .andExpect(status().isNoContent());

        Reminder movedReminder = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(movedReminder.getList().getId()).isEqualTo(defaultReminderList.getId());
        assertThat(reminderListRepository.existsById(reminderList.getId())).isFalse();
    }

    @Test
    void rejectsDeletingTheDefaultReminderList() throws Exception {
        mockMvc.perform(delete("/api/lists/{id}", defaultReminderList.getId()))
                .andExpect(status().isBadRequest());

        assertThat(reminderListRepository.existsById(defaultReminderList.getId())).isTrue();
    }

    @Test
    void returnsNotFoundWhenDeletingAMissingReminderList() throws Exception {
        mockMvc.perform(delete("/api/lists/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Reminder list with id 9999 was not found"));
    }

    @Test
    void createsAReminderInTheSpecifiedList() throws Exception {
        ReminderList reminderList = reminderListRepository.save(new ReminderList("School"));
        String request = """
                {
                  "title": "Study",
                  "dueDate": null,
                  "listId": %d
                }
                """.formatted(reminderList.getId());

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Study"))
                .andExpect(jsonPath("$.listId").value(reminderList.getId()));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getList().getId()).isEqualTo(reminderList.getId());
    }

    @Test
    void returnsNotFoundWhenCreatingAReminderWithAMissingList() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Study\",\"dueDate\":null,\"listId\":9999}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Reminder list with id 9999 was not found"));

        assertThat(reminderRepository.count()).isZero();
    }

    @Test
    void updatesAReminder() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Original title", null, null, defaultReminderList));
        String dueDate = LocalDate.now().plusDays(2).toString();
        String request = """
                {
                  "title": "Updated title",
                  "dueDate": "%s"
                }
                """.formatted(dueDate);

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(reminder.getId()))
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.dueDate").value(dueDate))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    void updatesPriorityFromNoneToHigh() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder(
                "Original title",
                null,
                null,
                defaultReminderList
        ));

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"dueDate\":null,\"priority\":\"HIGH\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));

        assertThat(reminderRepository.findById(reminder.getId()).orElseThrow().getPriority())
                .isEqualTo(ReminderPriority.HIGH);
    }

    @Test
    void updatesPriorityFromHighToLow() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder(
                "Original title",
                null,
                null,
                ReminderPriority.HIGH,
                defaultReminderList
        ));

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"dueDate\":null,\"priority\":\"LOW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("LOW"));

        assertThat(reminderRepository.findById(reminder.getId()).orElseThrow().getPriority())
                .isEqualTo(ReminderPriority.LOW);
    }

    @Test
    void defaultsOmittedPriorityToNoneWhenUpdating() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder(
                "Original title",
                null,
                null,
                ReminderPriority.HIGH,
                defaultReminderList
        ));

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"dueDate\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("NONE"));

        assertThat(reminderRepository.findById(reminder.getId()).orElseThrow().getPriority())
                .isEqualTo(ReminderPriority.NONE);
    }

    @Test
    void defaultsNullPriorityToNoneWhenUpdating() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder(
                "Original title",
                null,
                null,
                ReminderPriority.HIGH,
                defaultReminderList
        ));

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"dueDate\":null,\"priority\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("NONE"));

        assertThat(reminderRepository.findById(reminder.getId()).orElseThrow().getPriority())
                .isEqualTo(ReminderPriority.NONE);
    }

    @Test
    void updatesAReminderDueTime() throws Exception {
        String dueDate = LocalDate.now().plusDays(2).toString();
        Reminder reminder = reminderRepository.save(new Reminder(
                "Original title",
                LocalDate.parse(dueDate),
                null,
                defaultReminderList
        ));
        String request = """
                {
                  "title": "Updated title",
                  "dueDate": "%s",
                  "dueTime": "16:45"
                }
                """.formatted(dueDate);

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value(dueDate))
                .andExpect(jsonPath("$.dueTime").value("16:45"));

        Reminder updatedReminder = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(updatedReminder.getDueTime()).isEqualTo(LocalTime.of(16, 45));
    }

    @Test
    void rejectsDueTimeWithoutDueDateWhenUpdating() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder(
                "Original title",
                LocalDate.now().plusDays(2),
                null,
                defaultReminderList
        ));

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Updated title\",\"dueDate\":null,\"dueTime\":\"16:45\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.dueTime").value("Due time requires a due date"));
    }

    @Test
    void createsReminderWithTodayDueDate() throws Exception {
        String today = LocalDate.now().toString();
        String request = """
                {
                  "title": "Due today",
                  "dueDate": "%s"
                }
                """.formatted(today);

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").value(today));
    }

    @Test
    void rejectsPastDueDateWhenCreating() throws Exception {
        String request = """
                {
                  "title": "Past reminder",
                  "dueDate": "%s"
                }
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.dueDate").value("Due date must be today or later"));
    }

    @Test
    void rejectsPastDueDateWhenUpdating() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Original title", null, null, defaultReminderList));
        String request = """
                {
                  "title": "Updated title",
                  "dueDate": "%s"
                }
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.dueDate").value("Due date must be today or later"));
    }

    @Test
    void rejectsUnsupportedDueDateYearWhenCreating() throws Exception {
        String request = """
                {
                  "title": "Far future reminder",
                  "dueDate": "+10000-01-01"
                }
                """;

        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.dueDate").value("Due date year must be between 1000 and 9999"));
    }

    @Test
    void rejectsUnsupportedDueDateYearWhenUpdating() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Original title", null, null, defaultReminderList));
        String request = """
                {
                  "title": "Updated title",
                  "dueDate": "+10000-01-01"
                }
                """;

        mockMvc.perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.dueDate").value("Due date year must be between 1000 and 9999"));
    }

    @Test
    void ordersRemindersByCompletionScheduleTimePriorityAndCreationTime() throws Exception {
        LocalDate firstDate = LocalDate.of(2030, 1, 10);
        LocalDate secondDate = firstDate.plusDays(1);
        LocalDate timeComparisonDate = firstDate.plusDays(2);
        LocalDate priorityComparisonDate = firstDate.plusDays(3);
        LocalDate creationTimeComparisonDate = firstDate.plusDays(4);

        saveReminder(
                "earlier date low priority",
                false,
                firstDate,
                null,
                ReminderPriority.LOW,
                Instant.parse("2026-01-01T08:00:00Z")
        );
        saveReminder(
                "later date high priority",
                false,
                secondDate,
                null,
                ReminderPriority.HIGH,
                Instant.parse("2026-01-01T08:01:00Z")
        );
        saveReminder(
                "same date 09:00 low priority",
                false,
                timeComparisonDate,
                LocalTime.of(9, 0),
                ReminderPriority.LOW,
                Instant.parse("2026-01-01T08:02:00Z")
        );
        saveReminder(
                "same date 14:00 high priority",
                false,
                timeComparisonDate,
                LocalTime.of(14, 0),
                ReminderPriority.HIGH,
                Instant.parse("2026-01-01T08:03:00Z")
        );
        saveReminder(
                "same date no time high priority",
                false,
                timeComparisonDate,
                null,
                ReminderPriority.HIGH,
                Instant.parse("2026-01-01T08:04:00Z")
        );
        saveReminder(
                "priority high",
                false,
                priorityComparisonDate,
                LocalTime.NOON,
                ReminderPriority.HIGH,
                Instant.parse("2026-01-01T08:05:00Z")
        );
        saveReminder(
                "priority medium",
                false,
                priorityComparisonDate,
                LocalTime.NOON,
                ReminderPriority.MEDIUM,
                Instant.parse("2026-01-01T08:06:00Z")
        );
        saveReminder(
                "priority low",
                false,
                priorityComparisonDate,
                LocalTime.NOON,
                ReminderPriority.LOW,
                Instant.parse("2026-01-01T08:07:00Z")
        );
        saveReminder(
                "priority none",
                false,
                priorityComparisonDate,
                LocalTime.NOON,
                ReminderPriority.NONE,
                Instant.parse("2026-01-01T08:08:00Z")
        );
        saveReminder(
                "newer created at",
                false,
                creationTimeComparisonDate,
                LocalTime.NOON,
                ReminderPriority.MEDIUM,
                Instant.parse("2026-01-02T08:00:00Z")
        );
        saveReminder(
                "older created at",
                false,
                creationTimeComparisonDate,
                LocalTime.NOON,
                ReminderPriority.MEDIUM,
                Instant.parse("2026-01-01T08:00:00Z")
        );
        saveReminder(
                "unscheduled high priority",
                false,
                null,
                null,
                ReminderPriority.HIGH,
                Instant.parse("2026-01-01T08:09:00Z")
        );
        saveReminder(
                "completed earliest date high priority",
                true,
                firstDate.minusDays(1),
                LocalTime.of(8, 0),
                ReminderPriority.HIGH,
                Instant.parse("2026-01-01T08:10:00Z")
        );

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains(
                        "earlier date low priority",
                        "later date high priority",
                        "same date 09:00 low priority",
                        "same date 14:00 high priority",
                        "same date no time high priority",
                        "priority high",
                        "priority medium",
                        "priority low",
                        "priority none",
                        "newer created at",
                        "older created at",
                        "unscheduled high priority",
                        "completed earliest date high priority"
                )));
    }

    @Test
    void marksAReminderAsCompleted() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Finish report", null, null, defaultReminderList));

        mockMvc.perform(patch("/api/reminders/{id}/completion", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void deletesAReminder() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Remove me", null, null, defaultReminderList));

        mockMvc.perform(delete("/api/reminders/{id}", reminder.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsAnInvalidReminder() throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"dueDate\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.title").value("Title must not be blank"));
    }

    @Test
    void returnsNotFoundForUnknownReminder() throws Exception {
        mockMvc.perform(delete("/api/reminders/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Reminder with id 9999 was not found"));
    }

    private void createReminderWithPriority(ReminderPriority priority) throws Exception {
        mockMvc.perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Priority reminder\",\"priority\":\"" + priority + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value(priority.name()));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getPriority()).isEqualTo(priority);
    }

    private void saveReminder(
            String title,
            boolean completed,
            LocalDate dueDate,
            LocalTime dueTime,
            ReminderPriority priority,
            Instant createdAt
    ) {
        Reminder reminder = new Reminder(title, dueDate, dueTime, priority, defaultReminderList);
        reminder.setCompleted(completed);
        ReflectionTestUtils.setField(reminder, "createdAt", createdAt);
        reminderRepository.saveAndFlush(reminder);
    }
}
