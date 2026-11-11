package com.example.reminders.reminder;

import com.example.reminders.auth.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "reminder_lists")
public class ReminderList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // The migration leaves existing lists unowned. All new lists require a user.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, foreignKey = @ForeignKey(name = "fk_reminder_lists_user"))
    private AppUser user;

    @Column(name = "is_default", nullable = false, updatable = false)
    private boolean defaultList;

    protected ReminderList() {
    }

    public ReminderList(String name, AppUser user) {
        this(name, user, false);
    }

    private ReminderList(String name, AppUser user, boolean defaultList) {
        this.name = name;
        this.createdAt = Instant.now();
        this.user = Objects.requireNonNull(user, "A new reminder list requires an owner");
        this.defaultList = defaultList;
    }

    public static ReminderList createDefault(AppUser user) {
        return new ReminderList("Reminders", user, true);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public AppUser getUser() {
        return user;
    }

    public boolean isDefault() {
        return defaultList;
    }

    public void rename(String name) {
        this.name = name;
    }
}
