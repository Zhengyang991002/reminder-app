package com.example.reminders.reminder;

import com.example.reminders.common.BadRequestException;
import com.example.reminders.common.ResourceNotFoundException;
import com.example.reminders.reminder.dto.CreateReminderListRequest;
import com.example.reminders.reminder.dto.ReminderListResponse;
import com.example.reminders.reminder.dto.UpdateReminderListRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReminderListService {

    private static final String DEFAULT_REMINDER_LIST_NAME = "Reminders";

    private final ReminderListRepository reminderListRepository;
    private final ReminderRepository reminderRepository;

    public ReminderListService(
            ReminderListRepository reminderListRepository,
            ReminderRepository reminderRepository
    ) {
        this.reminderListRepository = reminderListRepository;
        this.reminderRepository = reminderRepository;
    }

    public List<ReminderListResponse> findAll() {
        return reminderListRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReminderListResponse create(CreateReminderListRequest request) {
        ReminderList reminderList = new ReminderList(request.name().trim());
        return toResponse(reminderListRepository.save(reminderList));
    }

    @Transactional
    public ReminderListResponse update(Long id, UpdateReminderListRequest request) {
        ReminderList reminderList = getReminderList(id);
        reminderList.rename(request.name().trim());
        return toResponse(reminderList);
    }

    @Transactional
    public void delete(Long id) {
        ReminderList reminderList = getReminderList(id);
        ReminderList defaultReminderList = getDefaultReminderList();

        if (reminderList.getId().equals(defaultReminderList.getId())) {
            throw new BadRequestException("The default reminder list cannot be deleted");
        }

        reminderRepository.findAllByList(reminderList)
                .forEach(reminder -> reminder.moveTo(defaultReminderList));
        reminderListRepository.delete(reminderList);
    }

    private ReminderList getReminderList(Long id) {
        return reminderListRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder list with id " + id + " was not found"));
    }

    private ReminderList getDefaultReminderList() {
        return reminderListRepository.findFirstByNameOrderByIdAsc(DEFAULT_REMINDER_LIST_NAME)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Reminder list named " + DEFAULT_REMINDER_LIST_NAME + " was not found"
                ));
    }

    private ReminderListResponse toResponse(ReminderList reminderList) {
        return new ReminderListResponse(
                reminderList.getId(),
                reminderList.getName(),
                reminderList.getCreatedAt()
        );
    }
}
