package com.example.reminders.reminder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reminder-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
class ReminderControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ReminderRepository reminderRepository;

    @BeforeEach
    void clearReminders() {
        reminderRepository.deleteAll();
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
                .andExpect(jsonPath("$.dueDate").value(dueDate));

        mockMvc.perform(get("/api/reminders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Buy groceries"));
    }

    @Test
    void updatesAReminder() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Original title", null));
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
        Reminder reminder = reminderRepository.save(new Reminder("Original title", null));
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
        Reminder reminder = reminderRepository.save(new Reminder("Original title", null));
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
    void marksAReminderAsCompleted() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Finish report", null));

        mockMvc.perform(patch("/api/reminders/{id}/completion", reminder.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    void deletesAReminder() throws Exception {
        Reminder reminder = reminderRepository.save(new Reminder("Remove me", null));

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
}
