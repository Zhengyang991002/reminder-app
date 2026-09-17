package com.example.reminders.reminder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReminderListRepository extends JpaRepository<ReminderList, Long> {

    Optional<ReminderList> findFirstByNameOrderByIdAsc(String name);
}
