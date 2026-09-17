package com.example.reminders.reminder;

import com.example.reminders.reminder.dto.CreateReminderListRequest;
import com.example.reminders.reminder.dto.ReminderListResponse;
import com.example.reminders.reminder.dto.UpdateReminderListRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lists")
public class ReminderListController {

    private final ReminderListService reminderListService;

    public ReminderListController(ReminderListService reminderListService) {
        this.reminderListService = reminderListService;
    }

    @GetMapping
    public List<ReminderListResponse> findAll() {
        return reminderListService.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReminderListResponse create(@Valid @RequestBody CreateReminderListRequest request) {
        return reminderListService.create(request);
    }

    @PutMapping("/{id}")
    public ReminderListResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReminderListRequest request
    ) {
        return reminderListService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        reminderListService.delete(id);
    }
}
