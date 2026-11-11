package com.example.reminders.reminder.dto;

import java.util.List;

public record ReminderPageResponse(
        List<ReminderResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
