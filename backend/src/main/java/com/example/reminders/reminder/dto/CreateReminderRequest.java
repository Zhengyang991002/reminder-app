package com.example.reminders.reminder.dto;

import com.example.reminders.reminder.validation.DueSchedule;
import com.example.reminders.reminder.validation.SupportedDueDateYear;
import com.example.reminders.reminder.validation.ValidDueSchedule;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;

@ValidDueSchedule
public record CreateReminderRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,
        @FutureOrPresent(message = "Due date must be today or later")
        @SupportedDueDateYear
        LocalDate dueDate,
        @JsonFormat(pattern = "HH:mm")
        LocalTime dueTime,
        Long listId
) implements DueSchedule {
}
