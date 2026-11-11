package com.example.reminders.reminder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

// Just like MyBatis Plus
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    String SEARCH_QUERY = """
            select reminder from Reminder reminder
            join fetch reminder.list
            where (lower(reminder.title) like lower(:pattern) escape '!'
                or lower(reminder.notes) like lower(:pattern) escape '!')
            """;

    List<Reminder> findAllByList(ReminderList list);

    @Query(SEARCH_QUERY)
    List<Reminder> searchAll(@Param("pattern") String pattern);

    @Query(SEARCH_QUERY + "and reminder.dueDate = :date")
    List<Reminder> searchByDate(@Param("pattern") String pattern, @Param("date") LocalDate date);

    @Query(SEARCH_QUERY + "and reminder.dueDate is not null")
    List<Reminder> searchScheduled(@Param("pattern") String pattern);

    @Query(SEARCH_QUERY + "and reminder.list = :list")
    List<Reminder> searchByList(@Param("pattern") String pattern, @Param("list") ReminderList list);
}
