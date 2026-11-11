package com.example.reminders.reminder;

import com.example.reminders.common.ResourceNotFoundException;
import com.example.reminders.reminder.dto.CreateReminderRequest;
import com.example.reminders.reminder.dto.ReminderResponse;
import com.example.reminders.reminder.dto.UpdateCompletionRequest;
import com.example.reminders.reminder.dto.UpdateReminderRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReminderService {

    private static final String DEFAULT_REMINDER_LIST_NAME = "Reminders";
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

    public List<ReminderResponse> findAll() {
        return reminderRepository.findAll()
                .stream()
                .sorted(REMINDER_ORDER)
                .map(this::toResponse)
                .toList();
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
