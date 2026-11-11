package com.example.reminders.auth;

import com.example.reminders.auth.dto.RegisterRequest;
import com.example.reminders.auth.dto.UserResponse;
import com.example.reminders.common.BadRequestException;
import com.example.reminders.reminder.ReminderList;
import com.example.reminders.reminder.ReminderListRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;

@Service
@Transactional(readOnly = true)
public class AuthService implements UserDetailsService {

    private static final String DUPLICATE_EMAIL_MESSAGE = "An account with that email already exists.";
    private static final String UNIQUE_VIOLATION_SQL_STATE = "23505";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReminderListRepository reminderListRepository;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ReminderListRepository reminderListRepository
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.reminderListRepository = reminderListRepository;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException(DUPLICATE_EMAIL_MESSAGE);
        }

        AppUser user = new AppUser(request.email(), passwordEncoder.encode(request.password()));
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            // Concurrent registration can pass the existence check. The unique constraint decides.
            if (exception.getMostSpecificCause() instanceof SQLException sqlException
                    && UNIQUE_VIOLATION_SQL_STATE.equals(sqlException.getSQLState())) {
                throw new BadRequestException(DUPLICATE_EMAIL_MESSAGE);
            }
            throw exception;
        }
        reminderListRepository.save(ReminderList.createDefault(user));
        return toResponse(user);
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        AppUser user = userRepository.findByEmail(CredentialPolicy.normalizeEmail(email))
                .orElseThrow(() -> new UsernameNotFoundException("User was not found"));
        return new ReminderPrincipal(user);
    }

    public UserResponse currentUser(Long id) {
        return userRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new UsernameNotFoundException("User was not found"));
    }

    private UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}
