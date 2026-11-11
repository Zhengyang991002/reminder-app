package com.example.reminders.reminder;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

// Just like MyBatis Plus
public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    String SEARCH_FILTER = """
            where ownedList.user.id = :ownerId
                and (lower(reminder.title) like lower(:pattern) escape '!'
                or lower(reminder.notes) like lower(:pattern) escape '!')
            """;

    String SEARCH_QUERY = "select reminder from Reminder reminder join fetch reminder.list ownedList " + SEARCH_FILTER;
    String SEARCH_COUNT_QUERY = "select count(reminder) from Reminder reminder join reminder.list ownedList "
            + SEARCH_FILTER;

    // Match V4's comparator before pagination. CASE makes null/priority ordering explicit
    // on PostgreSQL and H2; the unique ID only breaks otherwise identical ties.
    String PAGE_ORDER = """
            order by case when reminder.completed = true then 1 else 0 end,
                case when reminder.dueDate is null then 1 else 0 end, reminder.dueDate asc,
                case when reminder.dueTime is null then 1 else 0 end, reminder.dueTime asc,
                case reminder.priority
                    when com.example.reminders.reminder.ReminderPriority.HIGH then 0
                    when com.example.reminders.reminder.ReminderPriority.MEDIUM then 1
                    when com.example.reminders.reminder.ReminderPriority.LOW then 2
                    else 3 end,
                reminder.createdAt desc, reminder.id desc
            """;

    Optional<Reminder> findByIdAndList_User_Id(Long id, Long ownerId);

    List<Reminder> findAllByListAndList_User_Id(ReminderList list, Long ownerId);

    // Start at the lists so that even a list without reminders contributes a zero row.
    @Query("""
            select reminderList.id as listId, count(reminder.id) as reminderCount,
                sum(case when reminder.dueDate = :date then 1 else 0 end) as todayCount,
                sum(case when reminder.dueDate is not null then 1 else 0 end) as scheduledCount
            from ReminderList reminderList
            left join Reminder reminder on reminder.list = reminderList
            where reminderList.user.id = :ownerId
            group by reminderList.id
            order by reminderList.id
            """)
    List<ViewCountRow> countByView(@Param("ownerId") Long ownerId, @Param("date") LocalDate date);

    interface ViewCountRow {
        Long getListId();

        long getReminderCount();

        long getTodayCount();

        long getScheduledCount();
    }

    @Query(SEARCH_QUERY)
    List<Reminder> searchAll(@Param("ownerId") Long ownerId, @Param("pattern") String pattern);

    @Query(SEARCH_QUERY + "and reminder.dueDate = :date")
    List<Reminder> searchByDate(
            @Param("ownerId") Long ownerId, @Param("pattern") String pattern, @Param("date") LocalDate date
    );

    @Query(SEARCH_QUERY + "and reminder.dueDate is not null")
    List<Reminder> searchScheduled(@Param("ownerId") Long ownerId, @Param("pattern") String pattern);

    @Query(SEARCH_QUERY + "and reminder.list = :list")
    List<Reminder> searchByList(
            @Param("ownerId") Long ownerId, @Param("pattern") String pattern, @Param("list") ReminderList list
    );

    // This is a to-one fetch join: it does not multiply Reminder rows. Keep fetch
    // joins out of count queries, and do not add collection fetches to these pages.
    @Query(value = SEARCH_QUERY + PAGE_ORDER, countQuery = SEARCH_COUNT_QUERY)
    Page<Reminder> searchAll(@Param("ownerId") Long ownerId, @Param("pattern") String pattern, Pageable pageable);

    @Query(value = SEARCH_QUERY + "and reminder.dueDate = :date " + PAGE_ORDER,
            countQuery = SEARCH_COUNT_QUERY + "and reminder.dueDate = :date")
    Page<Reminder> searchByDate(
            @Param("ownerId") Long ownerId, @Param("pattern") String pattern, @Param("date") LocalDate date,
            Pageable pageable
    );

    @Query(value = SEARCH_QUERY + "and reminder.dueDate is not null " + PAGE_ORDER,
            countQuery = SEARCH_COUNT_QUERY + "and reminder.dueDate is not null")
    Page<Reminder> searchScheduled(
            @Param("ownerId") Long ownerId, @Param("pattern") String pattern, Pageable pageable
    );

    @Query(value = SEARCH_QUERY + "and reminder.list = :list " + PAGE_ORDER,
            countQuery = SEARCH_COUNT_QUERY + "and reminder.list = :list")
    Page<Reminder> searchByList(
            @Param("ownerId") Long ownerId, @Param("pattern") String pattern, @Param("list") ReminderList list,
            Pageable pageable
    );
}
