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

    private final ReminderRepository reminderRepository;

    public ReminderService(ReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    public List<ReminderResponse> findAll() {
        return reminderRepository.findAll(Sort.by(Sort.Order.asc("completed"), Sort.Order.desc("createdAt")))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReminderResponse create(CreateReminderRequest request) {
        Reminder reminder = new Reminder(request.title().trim(), request.dueDate());
        return toResponse(reminderRepository.save(reminder));
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
                reminder.getDueDate()
        );
    }
}
