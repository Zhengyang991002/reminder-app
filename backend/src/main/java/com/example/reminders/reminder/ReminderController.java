package com.example.reminders.reminder;

import com.example.reminders.reminder.dto.CreateReminderRequest;
import com.example.reminders.reminder.dto.ReminderResponse;
import com.example.reminders.reminder.dto.UpdateCompletionRequest;
import com.example.reminders.reminder.dto.UpdateReminderRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reminders")
public class ReminderController {

    private final ReminderService reminderService;

    public ReminderController(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @GetMapping
    public List<ReminderResponse> findAll() {
        return reminderService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderResponse create(@Valid @RequestBody CreateReminderRequest request) {
        return reminderService.create(request);
    }

    @PutMapping("/{id}")
    public ReminderResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReminderRequest request
    ) {
        return reminderService.update(id, request);
    }

    @PatchMapping("/{id}/completion")
    public ReminderResponse updateCompletion(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCompletionRequest request
    ) {
        return reminderService.updateCompletion(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        reminderService.delete(id);
    }
}
