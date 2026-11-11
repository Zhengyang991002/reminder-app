package com.example.reminders.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class CredentialPolicy {

    public static final int MAX_EMAIL_LENGTH = 254;
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_BYTES = 72;

    private CredentialPolicy() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public static boolean isPasswordPresent(String password) {
        return password != null && !password.isBlank();
    }

    public static boolean meetsMinimumPasswordStrength(String password) {
        return isPasswordPresent(password) && password.codePointCount(0, password.length()) >= MIN_PASSWORD_LENGTH;
    }

    public static boolean isPasswordEncodingSupported(String password) {
        return password == null || (password.length() <= MAX_PASSWORD_BYTES
                && password.indexOf('\0') < 0
                && StandardCharsets.UTF_8.newEncoder().canEncode(password)
                && password.getBytes(StandardCharsets.UTF_8).length <= MAX_PASSWORD_BYTES);
    }
}
