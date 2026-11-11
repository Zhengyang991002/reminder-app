package com.example.reminders.reminder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
        assertThat(reminder.getNotes()).isNull();
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

    @Test
    void normalizesNotesWithoutChangingInternalWhitespace() {
        String notes = "First line\n\n  Second line\tinside";
        Reminder reminder = new Reminder(
                "Call dentist", null, null, ReminderPriority.HIGH, reminderList,
                "\u2003 \n" + notes + "\t\u3000"
        );

        assertThat(reminder.getNotes()).isEqualTo(notes);

        reminder.update("Call dentist", null, null, ReminderPriority.HIGH, "\n Revised\n  Notes \t");

        assertThat(reminder.getNotes()).isEqualTo("Revised\n  Notes");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n\r", "\u2003\u3000"})
    void normalizesEmptyNotesToNullForConstructionAndUpdate(String notes) {
        Reminder reminder = new Reminder(
                "Call dentist", null, null, ReminderPriority.NONE, reminderList, notes
        );
        assertThat(reminder.getNotes()).isNull();

        reminder.update("Call dentist", null, null, ReminderPriority.NONE, "Existing notes");
        reminder.update("Call dentist", null, null, ReminderPriority.NONE, notes);

        assertThat(reminder.getNotes()).isNull();
    }

    @Test
    void acceptsNotesAtTheNormalizedJavaStringLengthLimit() {
        String notes = "\uD83D\uDE00".repeat(1000);
        assertThat(notes).hasSize(2000);

        Reminder reminder = new Reminder(
                "Call dentist", null, null, ReminderPriority.NONE, reminderList,
                " \n" + notes + "\t\u3000"
        );
        assertThat(reminder.getNotes()).isEqualTo(notes);

        reminder.update("Call dentist", null, null, ReminderPriority.NONE, " \t" + notes + "\n ");

        assertThat(reminder.getNotes()).isEqualTo(notes);
    }

    @Test
    void rejectsOversizedNotesWhenConstructingOutsideHttp() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Reminder(
                        "Call dentist", null, null, ReminderPriority.NONE, reminderList,
                        " \n" + "n".repeat(2001) + "\t "
                ))
                .withMessage("Notes must be at most 2000 characters");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Reminder(
                        "Call dentist", null, null, ReminderPriority.NONE, reminderList,
                        "\uD83D\uDE00".repeat(1000) + "x"
                ))
                .withMessage("Notes must be at most 2000 characters");
    }

    @Test
    void rejectsOversizedNotesBeforeChangingEntityState() {
        LocalDate dueDate = LocalDate.now().plusDays(1);
        LocalTime dueTime = LocalTime.of(14, 30);
        Reminder reminder = new Reminder(
                "Original title", dueDate, dueTime, ReminderPriority.HIGH, reminderList, "Original notes"
        );
        reminder.setCompleted(true);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> reminder.update(
                        "Changed title", dueDate.plusDays(1), LocalTime.of(9, 0), ReminderPriority.LOW,
                        "\uD83D\uDE00".repeat(1000) + "x"
                ))
                .withMessage("Notes must be at most 2000 characters");

        assertThat(reminder.getTitle()).isEqualTo("Original title");
        assertThat(reminder.getDueDate()).isEqualTo(dueDate);
        assertThat(reminder.getDueTime()).isEqualTo(dueTime);
        assertThat(reminder.getPriority()).isEqualTo(ReminderPriority.HIGH);
        assertThat(reminder.getNotes()).isEqualTo("Original notes");
        assertThat(reminder.getList()).isSameAs(reminderList);
        assertThat(reminder.isCompleted()).isTrue();
    }

    @Test
    void existingUpdateOverloadsPreserveNotes() {
        Reminder reminder = new Reminder(
                "Call dentist", null, null, ReminderPriority.HIGH, reminderList, "Keep these notes"
        );

        reminder.update("Changed title", null, null);

        assertThat(reminder.getPriority()).isEqualTo(ReminderPriority.HIGH);
        assertThat(reminder.getNotes()).isEqualTo("Keep these notes");

        reminder.update("Changed again", null, null, ReminderPriority.LOW);

        assertThat(reminder.getPriority()).isEqualTo(ReminderPriority.LOW);
        assertThat(reminder.getNotes()).isEqualTo("Keep these notes");
    }
}
