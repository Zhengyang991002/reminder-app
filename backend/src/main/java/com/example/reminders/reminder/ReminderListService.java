package com.example.reminders.reminder;

import com.example.reminders.auth.CurrentUser;
import com.example.reminders.auth.UserRepository;
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

    private final ReminderListRepository reminderListRepository;
    private final ReminderRepository reminderRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public ReminderListService(
            ReminderListRepository reminderListRepository,
            ReminderRepository reminderRepository,
            UserRepository userRepository,
            CurrentUser currentUser
    ) {
        this.reminderListRepository = reminderListRepository;
        this.reminderRepository = reminderRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    public List<ReminderListResponse> findAll() {
        return reminderListRepository.findAllByUser_IdOrderByIdAsc(currentUser.requireId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ReminderListResponse create(CreateReminderListRequest request) {
        Long ownerId = currentUser.requireId();
        ReminderList reminderList = new ReminderList(request.name().trim(), userRepository.getReferenceById(ownerId));
        return toResponse(reminderListRepository.save(reminderList));
    }

    @Transactional
    public ReminderListResponse update(Long id, UpdateReminderListRequest request) {
        ReminderList reminderList = getReminderListForUpdate(id, currentUser.requireId());
        reminderList.rename(request.name().trim());
        return toResponse(reminderList);
    }

    @Transactional
    public void delete(Long id) {
        Long ownerId = currentUser.requireId();
        ReminderList reminderList = getReminderListForUpdate(id, ownerId);

        if (reminderList.isDefault()) {
            throw new BadRequestException("The default reminder list cannot be deleted");
        }

        ReminderList defaultReminderList = reminderListRepository.findByUser_IdAndDefaultListTrue(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("The default reminder list was not found"));
        reminderRepository.findAllByListAndList_User_Id(reminderList, ownerId)
                .forEach(reminder -> reminder.moveTo(defaultReminderList));
        reminderListRepository.delete(reminderList);
    }

    private ReminderList getReminderListForUpdate(Long id, Long ownerId) {
        return reminderListRepository.findOwnedForUpdate(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Reminder list with id " + id + " was not found"));
    }

    private ReminderListResponse toResponse(ReminderList reminderList) {
        return new ReminderListResponse(
                reminderList.getId(),
                reminderList.getName(),
                reminderList.getCreatedAt(),
                reminderList.isDefault()
        );
    }
}
