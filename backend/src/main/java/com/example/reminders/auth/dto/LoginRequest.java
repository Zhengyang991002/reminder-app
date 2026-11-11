package com.example.reminders.auth.dto;

import com.example.reminders.auth.CredentialPolicy;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = CredentialPolicy.MAX_EMAIL_LENGTH, message = "Email must be at most 254 characters")
        String email,
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        String password
) {

    public LoginRequest {
        email = CredentialPolicy.normalizeEmail(email);
    }

    // Validate boolean results so a binding error never contains the rejected password.
    @JsonIgnore
    @AssertTrue(message = "Password is required")
    public boolean isPasswordPresent() {
        return CredentialPolicy.isPasswordPresent(password);
    }

    @JsonIgnore
    @AssertTrue(message = "Password must be valid text without null characters and at most 72 UTF-8 bytes")
    public boolean isPasswordEncodingSupported() {
        return CredentialPolicy.isPasswordEncodingSupported(password);
    }

    @Override
    public String toString() {
        return "LoginRequest[credentials=REDACTED]";
    }
}
