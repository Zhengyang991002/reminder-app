package com.example.reminders.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfLogoutHandler;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

import java.io.IOException;
import java.util.List;

import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(encoder);
        provider.setUserDetailsService(users);
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository sessionSecurityContextRepository() {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }

    @Bean
    HttpSessionCsrfTokenRepository sessionCsrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SessionAuthenticationStrategy loginSessionStrategy(HttpSessionCsrfTokenRepository tokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new CsrfAuthenticationStrategy(tokens)
        ));
    }

    @Bean
    LogoutHandler sessionLogoutHandler(
            HttpSessionCsrfTokenRepository tokens,
            HttpSessionSecurityContextRepository contexts,
            @Value("${server.servlet.session.cookie.name:JSESSIONID}") String cookieName
    ) {
        SecurityContextLogoutHandler contextLogout = new SecurityContextLogoutHandler();
        contextLogout.setSecurityContextRepository(contexts);
        return new CompositeLogoutHandler(
                new CsrfLogoutHandler(tokens),
                contextLogout,
                new CookieClearingLogoutHandler(cookieName)
        );
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationManager authenticationManager,
            HttpSessionSecurityContextRepository contexts,
            HttpSessionCsrfTokenRepository tokens,
            ObjectMapper mapper
    ) throws Exception {
        return http
                .authenticationManager(authenticationManager)
                .securityContext(context -> context.securityContextRepository(contexts).requireExplicitSave(true))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf.csrfTokenRepository(tokens))
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> writeProblem(
                                response, mapper, HttpStatus.UNAUTHORIZED, "Authentication is required."
                        ))
                        .accessDeniedHandler((request, response, exception) -> {
                            // CSRF runs before authorization. Anonymous business requests require login.
                            if (exception instanceof CsrfException && isBusinessRequest(request)
                                    && !CurrentUser.hasUser(SecurityContextHolder.getContext().getAuthentication())) {
                                writeProblem(response, mapper, HttpStatus.UNAUTHORIZED, "Authentication is required.");
                                return;
                            }
                            writeProblem(response, mapper, HttpStatus.FORBIDDEN,
                                    exception instanceof CsrfException ? "Invalid or missing CSRF token." : "Access is denied.");
                        }))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(request -> request.getDispatcherType() == DispatcherType.ERROR
                                && "/error".equals(request.getServletPath())).permitAll()
                        .requestMatchers(GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(GET, "/api/auth/me").authenticated()
                        .requestMatchers(POST, "/api/auth/logout").authenticated()
                        .requestMatchers(SecurityConfig::isBusinessRequest).hasRole("USER")
                        .anyRequest().denyAll())
                .build();
    }

    private static boolean isBusinessRequest(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.equals("/api/reminders") || path.startsWith("/api/reminders/")
                || path.equals("/api/lists") || path.startsWith("/api/lists/");
    }

    private static void writeProblem(
            HttpServletResponse response, ObjectMapper mapper, HttpStatus status, String detail
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
    }
}
