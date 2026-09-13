package com.example.reminders.reminder.dto;

import java.time.Instant;
import java.time.LocalDate;

public record ReminderResponse(
        Long id,
        String title,
        boolean completed,
        Instant createdAt,
        LocalDate dueDate
) {
}
