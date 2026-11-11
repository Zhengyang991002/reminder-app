package com.example.reminders.reminder;

import com.example.reminders.common.BadRequestException;
import com.example.reminders.common.ResourceNotFoundException;
import com.example.reminders.reminder.dto.CreateReminderRequest;
import com.example.reminders.reminder.dto.ReminderCountsResponse;
import com.example.reminders.reminder.dto.ReminderPageResponse;
import com.example.reminders.reminder.dto.ReminderResponse;
import com.example.reminders.reminder.dto.UpdateCompletionRequest;
import com.example.reminders.reminder.dto.UpdateReminderRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ReminderService {

    private static final String DEFAULT_REMINDER_LIST_NAME = "Reminders";
    private static final int MAX_SEARCH_QUERY_LENGTH = 200;
    // Legacy array endpoint ordering; pageable queries reproduce it in ReminderRepository.PAGE_ORDER.
    private static final Comparator<Reminder> REMINDER_ORDER = Comparator
            .comparingInt((Reminder reminder) -> reminder.isCompleted() ? 1 : 0)
            .thenComparing(Reminder::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Reminder::getDueTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparingInt(reminder -> priorityRank(reminder.getPriority()))
            .thenComparing(Reminder::getCreatedAt, Comparator.reverseOrder());

    private final ReminderRepository reminderRepository;
    private final ReminderListRepository reminderListRepository;

    public ReminderService(
            ReminderRepository reminderRepository,
            ReminderListRepository reminderListRepository
    ) {
        this.reminderRepository = reminderRepository;
        this.reminderListRepository = reminderListRepository;
    }

    public List<ReminderResponse> findAll(String query, String scope, String view, String listId, String today) {
        SearchCriteria criteria = searchCriteria(query, scope, view, listId, today);
        List<Reminder> matches = switch (criteria.view()) {
            case "today" -> reminderRepository.searchByDate(criteria.pattern(), criteria.date());
            case "scheduled" -> reminderRepository.searchScheduled(criteria.pattern());
            case "list" -> reminderRepository.searchByList(criteria.pattern(), criteria.list());
            default -> reminderRepository.searchAll(criteria.pattern());
        };

        return matches.stream()
                .sorted(REMINDER_ORDER)
                .map(this::toResponse)
                .toList();
    }

    public ReminderPageResponse findPage(
            String query, String scope, String view, String listId, String today, String page, String size
    ) {
        PageRequest pageable = pageRequest(page, size);
        SearchCriteria criteria = searchCriteria(query, scope, view, listId, today);
        Page<Reminder> matches = switch (criteria.view()) {
            case "today" -> reminderRepository.searchByDate(criteria.pattern(), criteria.date(), pageable);
            case "scheduled" -> reminderRepository.searchScheduled(criteria.pattern(), pageable);
            case "list" -> reminderRepository.searchByList(criteria.pattern(), criteria.list(), pageable);
            default -> reminderRepository.searchAll(criteria.pattern(), pageable);
        };

        // The database has already filtered, ordered and limited this page.
        int totalPages = matches.getTotalPages();
        return new ReminderPageResponse(
                matches.getContent().stream().map(this::toResponse).toList(),
                matches.getNumber(),
                matches.getSize(),
                matches.getTotalElements(),
                totalPages,
                matches.isFirst(),
                // Comparing with the last index avoids overflow at the largest page number.
                matches.getNumber() >= totalPages - 1
        );
    }

    public ReminderCountsResponse findCounts(String today) {
        LocalDate date = parseToday(today, "today is required");
        Map<Long, Long> lists = new LinkedHashMap<>();
        long allCount = 0;
        long todayCount = 0;
        long scheduledCount = 0;
        for (ReminderRepository.ViewCountRow row : reminderRepository.countByView(date)) {
            lists.put(row.getListId(), row.getReminderCount());
            allCount += row.getReminderCount();
            todayCount += row.getTodayCount();
            scheduledCount += row.getScheduledCount();
        }
        return new ReminderCountsResponse(allCount, todayCount, scheduledCount, lists);
    }

    private PageRequest pageRequest(String page, String size) {
        int pageNumber = parsePageInteger(page, "page", 0);
        int pageSize = parsePageInteger(size, "size", 20);
        if (pageNumber < 0) {
            throw new BadRequestException("page must be zero or greater");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        // JPA's setFirstResult accepts an int, even though Pageable uses a long offset.
        if ((long) pageNumber * pageSize > Integer.MAX_VALUE) {
            throw new BadRequestException("page and size exceed the supported database offset");
        }
        return PageRequest.of(pageNumber, pageSize);
    }

    private int parsePageInteger(String value, String name, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new BadRequestException(name + " must be a valid integer");
        }
    }

    private SearchCriteria searchCriteria(String query, String scope, String view, String listId, String today) {
        String pattern = searchPattern(query);
        if (scope == null || "global".equals(scope)) {
            if (view != null || listId != null || today != null) {
                throw new BadRequestException("view, listId and today require scope=view");
            }
            return new SearchCriteria(pattern, "all", null, null);
        }
        if (!"view".equals(scope)) {
            throw new BadRequestException("scope must be global or view");
        }
        if (view == null) {
            throw new BadRequestException("view is required when scope=view");
        }
        if (listId != null && !"list".equals(view)) {
            throw new BadRequestException("listId is only allowed when view=list");
        }
        if (today != null && !"today".equals(view)) {
            throw new BadRequestException("today is only allowed when view=today");
        }

        return switch (view) {
            case "all", "scheduled" -> new SearchCriteria(pattern, view, null, null);
            case "today" -> new SearchCriteria(
                    pattern, view, parseToday(today, "today is required when view=today"), null
            );
            case "list" -> new SearchCriteria(pattern, view, null, getReminderList(parseListId(listId)));
            default -> throw new BadRequestException("view must be all, today, scheduled or list");
        };
    }

    private record SearchCriteria(String pattern, String view, LocalDate date, ReminderList list) {
    }

    private String searchPattern(String query) {
        String normalized = query == null ? "" : query.strip();
        if (normalized.length() > MAX_SEARCH_QUERY_LENGTH) {
            throw new BadRequestException("q must be at most 200 characters after stripping whitespace");
        }
        if (normalized.indexOf('\0') >= 0) {
            throw new BadRequestException("q must not contain null characters");
        }
        if (normalized.isEmpty()) {
            return "%"; // Every reminder has a non-null title.
        }

        // The JPQL escape character is '!'. Backslash stays literal in a bound parameter.
        String escaped = normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        return "%" + escaped + "%";
    }

    private Long parseListId(String listId) {
        if (listId == null) {
            throw new BadRequestException("listId is required when view=list");
        }
        try {
            return Long.valueOf(listId);
        } catch (NumberFormatException exception) {
            throw new BadRequestException("listId must be a valid integer");
        }
    }

    private LocalDate parseToday(String today, String requiredMessage) {
        if (today == null) {
            throw new BadRequestException(requiredMessage);
        }
        if (today.length() != 10) {
            throw new BadRequestException("today must use YYYY-MM-DD");
        }
        try {
            return LocalDate.parse(today);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException("today must be a valid ISO date (YYYY-MM-DD)");
        }
    }

    @Transactional
    public ReminderResponse create(CreateReminderRequest request) {
        ReminderList reminderList = request.listId() == null
                ? getDefaultReminderList()
                : getReminderList(request.listId());
        Reminder reminder = new Reminder(
                request.title().trim(),
                request.dueDate(),
                request.dueTime(),
                defaultPriority(request.priority()),
                reminderList,
                request.notes()
        );
        return toResponse(reminderRepository.save(reminder));
    }

    private ReminderList getDefaultReminderList() {
        return reminderListRepository.findFirstByNameOrderByIdAsc(DEFAULT_REMINDER_LIST_NAME)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reminder list named " + DEFAULT_REMINDER_LIST_NAME + " was not found"
                ));
    }

    private ReminderList getReminderList(Long id) {
        return reminderListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder list with id " + id + " was not found"));
    }

    @Transactional
    public ReminderResponse update(Long id, UpdateReminderRequest request) {
        Reminder reminder = getReminder(id);
        reminder.update(
                request.title().trim(),
                request.dueDate(),
                request.dueTime(),
                defaultPriority(request.priority()),
                request.notes()
        );
        return toResponse(reminder);
    }

    @Transactional
    public ReminderResponse updateCompletion(Long id, UpdateCompletionRequest request) {
        Reminder reminder = getReminder(id);
        reminder.setCompleted(request.completed());
        return toResponse(reminder);
    }

    @Transactional
    public void delete(Long id) {
        reminderRepository.delete(getReminder(id));
    }

    private Reminder getReminder(Long id) {
        return reminderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder with id " + id + " was not found"));
    }

    private ReminderPriority defaultPriority(ReminderPriority priority) {
        return priority == null ? ReminderPriority.NONE : priority;
    }

    private static int priorityRank(ReminderPriority priority) {
        return switch (priority) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
            case NONE -> 3;
        };
    }

    private ReminderResponse toResponse(Reminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getTitle(),
                reminder.isCompleted(),
                reminder.getCreatedAt(),
                reminder.getDueDate(),
                reminder.getDueTime(),
                reminder.getPriority(),
                reminder.getList().getId(),
                reminder.getNotes()
        );
    }
}
