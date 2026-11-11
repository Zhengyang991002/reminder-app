package com.example.reminders.reminder.dto;

import java.util.Map;

public record ReminderCountsResponse(
        long all,
        long today,
        long scheduled,
        Map<Long, Long> lists
) {
}
