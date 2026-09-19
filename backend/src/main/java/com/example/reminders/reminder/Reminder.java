package com.example.reminders.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "reminders")
public class Reminder {
    // primary key
    @Id
    // generate by database
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false)
    private boolean completed;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private LocalDate dueDate;

    @Column(name = "due_time")
    private LocalTime dueTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private ReminderPriority priority = ReminderPriority.NONE;

    @ManyToOne(optional = false)
    @JoinColumn(name = "list_id", nullable = false)
    private ReminderList list;

    // So no args construction is limited
    protected Reminder() {
    }

    public Reminder(String title, LocalDate dueDate, LocalTime dueTime, ReminderList list) {
        this(title, dueDate, dueTime, ReminderPriority.NONE, list);
    }

    public Reminder(
            String title,
            LocalDate dueDate,
            LocalTime dueTime,
            ReminderPriority priority,
            ReminderList list
    ) {
        validateDueSchedule(dueDate, dueTime);
        this.title = title;
        this.completed = false;
        this.createdAt = Instant.now();
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.priority = requirePriority(priority);
        this.list = list;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public boolean isCompleted() {
        return completed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalTime getDueTime() {
        return dueTime;
    }

    public ReminderPriority getPriority() {
        return priority;
    }

    public ReminderList getList() {
        return list;
    }

    public void moveTo(ReminderList list) {
        this.list = list;
    }

    public void update(String title, LocalDate dueDate, LocalTime dueTime) {
        update(title, dueDate, dueTime, priority);
    }

    public void update(String title, LocalDate dueDate, LocalTime dueTime, ReminderPriority priority) {
        validateDueSchedule(dueDate, dueTime);
        this.title = title;
        this.dueDate = dueDate;
        this.dueTime = dueTime;
        this.priority = requirePriority(priority);
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    private void validateDueSchedule(LocalDate dueDate, LocalTime dueTime) {
        if (dueTime != null && dueDate == null) {
            throw new IllegalArgumentException("Due time requires a due date");
        }
    }

    private ReminderPriority requirePriority(ReminderPriority priority) {
        if (priority == null) {
            throw new IllegalArgumentException("Priority must not be null");
        }
        return priority;
    }
}
