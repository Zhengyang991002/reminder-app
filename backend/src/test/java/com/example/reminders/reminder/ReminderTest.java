package com.example.reminders.reminder;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ReminderTest {

    private final ReminderList reminderList = new ReminderList("Reminders");

    @Test
    void rejectsDueTimeWithoutDueDateWhenConstructing() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Reminder(
                        "Call dentist",
                        null,
                        LocalTime.of(14, 30),
                        reminderList
                ))
                .withMessage("Due time requires a due date");
    }

    @Test
    void rejectsDueTimeWithoutDueDateWhenUpdating() {
        LocalDate dueDate = LocalDate.now().plusDays(1);
        Reminder reminder = new Reminder("Call dentist", dueDate, null, reminderList);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> reminder.update("Call dentist", null, LocalTime.of(14, 30)))
                .withMessage("Due time requires a due date");

        assertThat(reminder.getDueDate()).isEqualTo(dueDate);
        assertThat(reminder.getDueTime()).isNull();
    }

    @Test
    void defaultsOmittedPriorityToNone() {
        Reminder reminder = new Reminder("Call dentist", null, null, reminderList);

        assertThat(reminder.getPriority()).isEqualTo(ReminderPriority.NONE);
    }

    @Test
    void preservesCurrentPriorityWhenUpdatingWithoutPriority() {
        Reminder reminder = new Reminder(
                "Call dentist",
                null,
                null,
                ReminderPriority.HIGH,
                reminderList
        );

        reminder.update("Call dentist", null, null);

        assertThat(reminder.getPriority()).isEqualTo(ReminderPriority.HIGH);
    }

    @Test
    void rejectsNullPriority() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Reminder("Call dentist", null, null, null, reminderList))
                .withMessage("Priority must not be null");

        Reminder reminder = new Reminder("Call dentist", null, null, reminderList);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> reminder.update("Call dentist", null, null, null))
                .withMessage("Priority must not be null");
    }
}
