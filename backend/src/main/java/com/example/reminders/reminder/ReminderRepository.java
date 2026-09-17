package com.example.reminders.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

// Just like MyBatis Plus
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    List<Reminder> findAllByList(ReminderList list);
}
