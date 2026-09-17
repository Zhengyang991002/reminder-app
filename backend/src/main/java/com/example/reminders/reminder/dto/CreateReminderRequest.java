package com.example.reminders.reminder.dto;

import com.example.reminders.reminder.validation.SupportedDueDateYear;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateReminderRequest(
        @NotBlank(message = "Title must not be blank")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,
        @FutureOrPresent(message = "Due date must be today or later")
        @SupportedDueDateYear
        LocalDate dueDate,
        Long listId
) {
}
