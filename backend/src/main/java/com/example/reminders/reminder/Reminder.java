package com.example.reminders.reminder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

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
    // So no args construction is limited
    protected Reminder() {
    }

    public Reminder(String title, LocalDate dueDate) {
        this.title = title;
        this.completed = false;
        this.createdAt = Instant.now();
        this.dueDate = dueDate;
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

    public void update(String title, LocalDate dueDate) {
        this.title = title;
        this.dueDate = dueDate;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }
}
