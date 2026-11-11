package com.example.reminders.reminder;

import com.example.reminders.common.BadRequestException;
import com.example.reminders.common.ResourceNotFoundException;
import com.example.reminders.reminder.dto.CreateReminderRequest;
import com.example.reminders.reminder.dto.ReminderResponse;
import com.example.reminders.reminder.dto.UpdateCompletionRequest;
import com.example.reminders.reminder.dto.UpdateReminderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReminderService {

    private static final String DEFAULT_REMINDER_LIST_NAME = "Reminders";
    private static final int MAX_SEARCH_QUERY_LENGTH = 200;
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
        String pattern = searchPattern(query);
        List<Reminder> matches;
        if (scope == null || "global".equals(scope)) {
            if (view != null || listId != null || today != null) {
                throw new BadRequestException("view, listId and today require scope=view");
            }
            matches = reminderRepository.searchAll(pattern);
        } else if ("view".equals(scope)) {
            matches = searchView(pattern, view, listId, today);
        } else {
            throw new BadRequestException("scope must be global or view");
        }

        return matches.stream()
                .sorted(REMINDER_ORDER)
                .map(this::toResponse)
                .toList();
    }

    private List<Reminder> searchView(String pattern, String view, String listId, String today) {
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
            case "all" -> reminderRepository.searchAll(pattern);
            case "today" -> reminderRepository.searchByDate(pattern, parseToday(today));
            case "scheduled" -> reminderRepository.searchScheduled(pattern);
            case "list" -> reminderRepository.searchByList(pattern, getReminderList(parseListId(listId)));
            default -> throw new BadRequestException("view must be all, today, scheduled or list");
        };
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

    private LocalDate parseToday(String today) {
        if (today == null) {
            throw new BadRequestException("today is required when view=today");
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
