package com.example.reminders.reminder.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateCompletionRequest(
        @NotNull(message = "Completed must be provided")
        Boolean completed
) {
}
