package com.example.reminders.auth;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

import java.io.Serial;

final class ReminderPrincipal extends User {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long userId;

    ReminderPrincipal(AppUser user) {
        super(user.getEmail(), user.getPasswordHash(), AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.userId = user.getId();
    }

    Long userId() {
        return userId;
    }
}
