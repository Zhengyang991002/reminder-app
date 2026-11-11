package com.example.reminders.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {

    public Long requireId() {
        return requireId(SecurityContextHolder.getContext().getAuthentication());
    }

    static Long requireId(Authentication authentication) {
        if (hasUser(authentication)) {
            return ((ReminderPrincipal) authentication.getPrincipal()).userId();
        }
        throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
    }

    static boolean hasUser(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof ReminderPrincipal;
    }
}
