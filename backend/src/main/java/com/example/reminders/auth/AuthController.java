package com.example.reminders.auth;

import com.example.reminders.auth.dto.LoginRequest;
import com.example.reminders.auth.dto.RegisterRequest;
import com.example.reminders.auth.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService users;
    private final AuthenticationManager authenticationManager;
    private final SessionAuthenticationStrategy loginSessionStrategy;
    private final HttpSessionSecurityContextRepository contexts;
    private final LogoutHandler sessionLogoutHandler;

    public AuthController(
            AuthService users,
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy loginSessionStrategy,
            HttpSessionSecurityContextRepository contexts,
            LogoutHandler sessionLogoutHandler
    ) {
        this.users = users;
        this.authenticationManager = authenticationManager;
        this.loginSessionStrategy = loginSessionStrategy;
        this.contexts = contexts;
        this.sessionLogoutHandler = sessionLogoutHandler;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return users.register(request);
    }

    @PostMapping("/login")
    public UserResponse login(
            @Valid @RequestBody LoginRequest credentials,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(credentials.email(), credentials.password())
        );
        UserResponse user = users.currentUser(CurrentUser.requireId(authentication));
        loginSessionStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return user;
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return users.currentUser(CurrentUser.requireId(authentication));
    }

    @GetMapping("/csrf")
    public ResponseEntity<CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new CsrfResponse(token.getHeaderName(), token.getToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        sessionLogoutHandler.logout(request, response, authentication);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail invalidCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail unreadableCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Malformed authentication request.");
    }

    public record CsrfResponse(String headerName, String token) {

        @Override
        public String toString() {
            return "CsrfResponse[headerName=" + headerName + ", token=<redacted>]";
        }
    }
}
