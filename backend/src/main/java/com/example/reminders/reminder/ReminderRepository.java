package com.example.reminders.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

// Just like MyBatis Plus
public interface ReminderRepository extends JpaRepository<Reminder, Long> {
}
