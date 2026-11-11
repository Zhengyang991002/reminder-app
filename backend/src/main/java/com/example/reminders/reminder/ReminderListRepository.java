package com.example.reminders.reminder;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ReminderListRepository extends JpaRepository<ReminderList, Long> {

    List<ReminderList> findAllByUser_IdOrderByIdAsc(Long ownerId);

    Optional<ReminderList> findByIdAndUser_Id(Long id, Long ownerId);

    Optional<ReminderList> findByUser_IdAndDefaultListTrue(Long ownerId);

    // List deletion and creation of reminders in the list use the same row lock.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select reminderList
            from ReminderList reminderList
            where reminderList.id = :id and reminderList.user.id = :ownerId
            """)
    Optional<ReminderList> findOwnedForUpdate(@Param("id") Long id, @Param("ownerId") Long ownerId);
}
