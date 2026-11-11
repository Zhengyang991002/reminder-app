package com.example.reminders.reminder;

public final class ReminderNotes {

    public static final int MAX_LENGTH = 2000;

    private ReminderNotes() {
    }

    public static String normalize(String notes) {
        if (notes == null) {
            return null;
        }

        String normalized = notes.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
