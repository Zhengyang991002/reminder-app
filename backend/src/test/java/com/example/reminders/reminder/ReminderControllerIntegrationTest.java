package com.example.reminders.reminder;

import com.example.reminders.auth.AppUser;
import com.example.reminders.auth.AuthenticatedTestSession;
import com.example.reminders.auth.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
    private ObjectMapper objectMapper;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private ReminderListRepository reminderListRepository;

    @Autowired
    private UserRepository userRepository;

    private AuthenticatedTestSession session;
    private AppUser owner;

    private ReminderList defaultReminderList;

    @BeforeEach
    void clearReminders() throws Exception {
        reminderRepository.deleteAllInBatch();
        reminderListRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
        session = AuthenticatedTestSession.registerAndLogin(mockMvc, objectMapper, "regression@example.com");
        owner = userRepository.findById(session.userId()).orElseThrow();
        defaultReminderList = reminderListRepository.findByUser_IdAndDefaultListTrue(session.userId()).orElseThrow();
    }

    private ResultActions perform(MockHttpServletRequestBuilder request) throws Exception {
        return session.perform(mockMvc, request);
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

        perform(post("/api/reminders")
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

        perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Buy groceries"))
                .andExpect(jsonPath("$[0].listId").value(defaultReminderList.getId()));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getList().getId()).isEqualTo(defaultReminderList.getId());
    }

    @Test
    void createsReminderWithoutDueDateOrDueTime() throws Exception {
        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No schedule\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.dueDate").isEmpty())
                .andExpect(jsonPath("$.dueTime").isEmpty())
                .andExpect(jsonPath("$.notes").hasJsonPath())
                .andExpect(jsonPath("$.notes").value(nullValue()));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getDueDate()).isNull();
        assertThat(createdReminder.getDueTime()).isNull();
        assertThat(createdReminder.getNotes()).isNull();
    }

    @Test
    void createsAndListsReminderWithNormalizedMultilineNotes() throws Exception {
        String notes = "First line\n\n  Indented second line\tinside";

        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequestWithNotes("\u2003 \n" + notes + "\n \u3000")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").value(notes));

        perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].notes").value(notes));

        assertThat(reminderRepository.findAll().get(0).getNotes()).isEqualTo(notes);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n\r", "\u2003\u3000"})
    void createsReminderWithNullNotesForEmptyContent(String notes) throws Exception {
        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequestWithNotes(notes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").hasJsonPath())
                .andExpect(jsonPath("$.notes").value(nullValue()));

        assertThat(reminderRepository.findAll().get(0).getNotes()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "\uD83D\uDE00"})
    void acceptsExactly2000JavaStringCharactersInNotes(String character) throws Exception {
        String notes = character.repeat(2000 / character.length());
        assertThat(notes).hasSize(2000);

        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequestWithNotes(notes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").value(notes));

        assertThat(reminderRepository.findAll().get(0).getNotes()).isEqualTo(notes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "\uD83D\uDE00"})
    void rejectsOversizedNormalizedNotesWhenCreating(String character) throws Exception {
        String notes = character.repeat(2000 / character.length()) + "x";

        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequestWithNotes(" \n" + notes + "\t ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.notes").value("Notes must be at most 2000 characters"));

        assertThat(reminderRepository.count()).isZero();
    }

    @Test
    void acceptsRawNotesOver2000CharactersAfterNormalizationWhenCreating() throws Exception {
        String notes = "n".repeat(2000);
        String rawNotes = " \t\u2003" + notes + "\n\u3000 ";
        assertThat(rawNotes.length()).isGreaterThan(2000);

        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequestWithNotes(rawNotes)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notes").value(notes));

        assertThat(reminderRepository.findAll().get(0).getNotes()).isEqualTo(notes);
    }

    @Test
    void updatesNotesWhilePreservingOtherReminderState() throws Exception {
        LocalDate dueDate = LocalDate.now().plusDays(1);
        LocalTime dueTime = LocalTime.of(14, 30);
        ReminderList list = reminderListRepository.save(new ReminderList("School", owner));
        Reminder reminder = new Reminder(
                "Notes reminder", dueDate, dueTime, ReminderPriority.HIGH, list, "Original notes"
        );
        reminder.setCompleted(true);
        reminderRepository.save(reminder);
        Instant createdAt = reminderRepository.findById(reminder.getId()).orElseThrow().getCreatedAt();
        String notes = "Revised first line\n  Keep this indentation";
        String request = objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("title", reminder.getTitle())
                .put("dueDate", dueDate.toString())
                .put("dueTime", "14:30")
                .put("priority", "HIGH")
                .put("notes", " \n" + notes + "\t "));

        perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value(notes))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueDate").value(dueDate.toString()))
                .andExpect(jsonPath("$.dueTime").value("14:30"))
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.listId").value(list.getId()));

        Reminder updatedReminder = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(updatedReminder.getNotes()).isEqualTo(notes);
        assertThat(updatedReminder.getPriority()).isEqualTo(ReminderPriority.HIGH);
        assertThat(updatedReminder.getDueDate()).isEqualTo(dueDate);
        assertThat(updatedReminder.getDueTime()).isEqualTo(dueTime);
        assertThat(updatedReminder.isCompleted()).isTrue();
        assertThat(updatedReminder.getList().getId()).isEqualTo(list.getId());
        assertThat(updatedReminder.getCreatedAt()).isEqualTo(createdAt);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n\r", "\u2003\u3000"})
    void clearsNotesWithNullOrBlankInputWhenUpdating(String notes) throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder(
                "Notes reminder", null, null, ReminderPriority.NONE, defaultReminderList, "Original notes"
        ));

        perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequestWithNotes(notes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").hasJsonPath())
                .andExpect(jsonPath("$.notes").value(nullValue()));

        assertThat(reminderRepository.findById(reminder.getId()).orElseThrow().getNotes()).isNull();
    }

    @Test
    void clearsNotesWhenOmittedFromFullUpdate() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder(
                "Notes reminder", null, null, ReminderPriority.NONE, defaultReminderList, "Original notes"
        ));

        perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Notes reminder\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").hasJsonPath())
                .andExpect(jsonPath("$.notes").value(nullValue()));

        assertThat(reminderRepository.findById(reminder.getId()).orElseThrow().getNotes()).isNull();
    }

    @Test
    void rejectsOversizedNotesWhenUpdatingWithoutChangingExistingReminder() throws Exception {
        LocalDate dueDate = LocalDate.now().plusDays(1);
        Reminder reminder = reminderRepository.save(new Reminder(
                "Original title", dueDate, LocalTime.of(14, 30), ReminderPriority.HIGH,
                defaultReminderList, "Original notes"
        ));

        perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequestWithNotes(" \n" + "n".repeat(2001) + "\t ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.notes").value("Notes must be at most 2000 characters"));

        Reminder unchangedReminder = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(unchangedReminder.getTitle()).isEqualTo("Original title");
        assertThat(unchangedReminder.getNotes()).isEqualTo("Original notes");
        assertThat(unchangedReminder.getDueDate()).isEqualTo(dueDate);
        assertThat(unchangedReminder.getDueTime()).isEqualTo(LocalTime.of(14, 30));
        assertThat(unchangedReminder.getPriority()).isEqualTo(ReminderPriority.HIGH);
    }

    @Test
    void acceptsRawNotesOver2000CharactersAfterNormalizationWhenUpdating() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder(
                "Notes reminder", null, null, ReminderPriority.NONE, defaultReminderList, "Original notes"
        ));
        String notes = "n".repeat(2000);

        perform(put("/api/reminders/{id}", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reminderRequestWithNotes("\u2003 \n" + notes + "\t\u3000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes").value(notes));

        assertThat(reminderRepository.findById(reminder.getId()).orElseThrow().getNotes()).isEqualTo(notes);
    }

    @Test
    void completionPatchPreservesNotesAndSchedule() throws Exception {
        LocalDate dueDate = LocalDate.now().plusDays(1);
        String notes = "First line\nSecond line";
        Reminder reminder = reminderRepository.save(new Reminder(
                "Notes reminder", dueDate, LocalTime.of(9, 0), ReminderPriority.MEDIUM,
                defaultReminderList, notes
        ));

        perform(patch("/api/reminders/{id}/completion", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.notes").value(notes))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.dueDate").value(dueDate.toString()))
                .andExpect(jsonPath("$.dueTime").value("09:00"));

        assertThat(reminderRepository.findById(reminder.getId()).orElseThrow().getNotes()).isEqualTo(notes);
    }

    @Test
    void defaultsOmittedPriorityToNoneWhenCreating() throws Exception {
        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"No priority\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority").value("NONE"));

        Reminder createdReminder = reminderRepository.findAll().get(0);
        assertThat(createdReminder.getPriority()).isEqualTo(ReminderPriority.NONE);
    }

    @Test
    void createsReminderWithExplicitNonePriority() throws Exception {
        perform(post("/api/reminders")
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
        perform(post("/api/reminders")
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

        perform(post("/api/reminders")
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
        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Call dentist\",\"dueDate\":null,\"dueTime\":\"14:30\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.dueTime").value("Due time requires a due date"));
    }

    @Test
    void listsReminderLists() throws Exception {
        reminderListRepository.save(new ReminderList("School", owner));

        perform(get("/api/lists"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].name", hasItems("Reminders", "School")));
    }

    @Test
    void createsAReminderList() throws Exception {
        perform(post("/api/lists")
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

        perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        assertThat(reminderListRepository.findAll())
                .extracting(ReminderList::getName)
                .containsExactlyInAnyOrder("Reminders", "School", "School");
    }

    @Test
    void rejectsBlankReminderListName() throws Exception {
        perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.name").value("Name must not be blank"));
    }

    @Test
    void rejectsReminderListNameLongerThan100Characters() throws Exception {
        String name = "a".repeat(101);

        perform(post("/api/lists")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.name").value("Name must be at most 100 characters"));
    }

    @Test
    void renamesAReminderList() throws Exception {
        ReminderList reminderList = reminderListRepository.save(new ReminderList("School", owner));

        perform(put("/api/lists/{id}", reminderList.getId())
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
        perform(put("/api/lists/{id}", 9999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"CS5010\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Reminder list with id 9999 was not found"));
    }

    @Test
    void deletesANormalReminderList() throws Exception {
        ReminderList reminderList = reminderListRepository.save(new ReminderList("School", owner));

        perform(delete("/api/lists/{id}", reminderList.getId()))
                .andExpect(status().isNoContent());

        assertThat(reminderListRepository.existsById(reminderList.getId())).isFalse();
    }

    @Test
    void movesRemindersToTheDefaultListBeforeDeletingAReminderList() throws Exception {
        ReminderList reminderList = reminderListRepository.save(new ReminderList("School", owner));
        Reminder reminder = reminderRepository.save(new Reminder("Study", null, null, reminderList));

        perform(delete("/api/lists/{id}", reminderList.getId()))
                .andExpect(status().isNoContent());

        Reminder movedReminder = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertThat(movedReminder.getList().getId()).isEqualTo(defaultReminderList.getId());
        assertThat(reminderListRepository.existsById(reminderList.getId())).isFalse();
    }

    @Test
    void rejectsDeletingTheDefaultReminderList() throws Exception {
        perform(delete("/api/lists/{id}", defaultReminderList.getId()))
                .andExpect(status().isBadRequest());

        assertThat(reminderListRepository.existsById(defaultReminderList.getId())).isTrue();
    }

    @Test
    void returnsNotFoundWhenDeletingAMissingReminderList() throws Exception {
        perform(delete("/api/lists/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Reminder list with id 9999 was not found"));
    }

    @Test
    void createsAReminderInTheSpecifiedList() throws Exception {
        ReminderList reminderList = reminderListRepository.save(new ReminderList("School", owner));
        String request = """
                {
                  "title": "Study",
                  "dueDate": null,
                  "listId": %d
                }
                """.formatted(reminderList.getId());

        perform(post("/api/reminders")
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
        perform(post("/api/reminders")
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(post("/api/reminders")
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

        perform(post("/api/reminders")
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(post("/api/reminders")
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

        perform(put("/api/reminders/{id}", reminder.getId())
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

        perform(get("/api/reminders"))
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

        perform(patch("/api/reminders/{id}/completion", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void deletesAReminder() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Remove me", null, null, defaultReminderList));

        perform(delete("/api/reminders/{id}", reminder.getId()))
                .andExpect(status().isNoContent());

        perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsAnInvalidReminder() throws Exception {
        perform(post("/api/reminders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"   \",\"dueDate\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request validation failed"))
                .andExpect(jsonPath("$.errors.title").value("Title must not be blank"));
    }

    @Test
    void returnsNotFoundForUnknownReminder() throws Exception {
        perform(delete("/api/reminders/{id}", 9999))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Reminder with id 9999 was not found"));
    }

    private String reminderRequestWithNotes(String notes) throws Exception {
        return objectMapper.writeValueAsString(objectMapper.createObjectNode()
                .put("title", "Notes reminder")
                .put("notes", notes));
    }

    private void createReminderWithPriority(ReminderPriority priority) throws Exception {
        perform(post("/api/reminders")
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
