package com.example.reminders.reminder.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReminderResponse(
        Long id,
        String title,
        boolean completed,
        Instant createdAt,
        LocalDate dueDate,
        @JsonFormat(pattern = "HH:mm")
        LocalTime dueTime,
        Long listId
) {
}
