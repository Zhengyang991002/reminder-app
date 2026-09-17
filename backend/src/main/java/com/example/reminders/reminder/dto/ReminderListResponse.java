package com.example.reminders.reminder.dto;

import java.time.Instant;

public record ReminderListResponse(
        Long id,
        String name,
        Instant createdAt
) {
}
