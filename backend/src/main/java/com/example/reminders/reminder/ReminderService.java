package com.example.reminders.reminder;

import com.example.reminders.common.ResourceNotFoundException;
import com.example.reminders.reminder.dto.CreateReminderRequest;
import com.example.reminders.reminder.dto.ReminderResponse;
import com.example.reminders.reminder.dto.UpdateCompletionRequest;
import com.example.reminders.reminder.dto.UpdateReminderRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReminderService {

    private static final String DEFAULT_REMINDER_LIST_NAME = "Reminders";

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
        return reminderRepository.findAll(Sort.by(Sort.Order.asc("completed"), Sort.Order.desc("createdAt")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReminderResponse create(CreateReminderRequest request) {
        ReminderList reminderList = request.listId() == null
                ? getDefaultReminderList()
                : getReminderList(request.listId());
        Reminder reminder = new Reminder(request.title().trim(), request.dueDate(), reminderList);
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
        reminder.update(request.title().trim(), request.dueDate());
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

    private ReminderResponse toResponse(Reminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getTitle(),
                reminder.isCompleted(),
                reminder.getCreatedAt(),
                reminder.getDueDate(),
                reminder.getList().getId()
        );
    }
}
