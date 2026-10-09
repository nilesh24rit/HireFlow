package com.hireflow.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.hireflow.auth.security.SecurityErrorResponseHandler;

/**
 * Explicit security foundation for auth-service.
 *
 * <p>During the Step 12 inspection no {@link SecurityFilterChain} existed anywhere in the
 * repository, so Spring Boot's implicit default applied: every request required
 * authentication, but a generated HTML form login page and session-based login were also
 * exposed as side effects. This configuration states the intended rules deliberately and
 * keeps security entirely outside controllers and services:
 *
 * <ul>
 *   <li><b>Every endpoint requires authentication.</b> No {@code permitAll} rule exists, so
 *       no protected endpoint can be exposed accidentally. Endpoint-specific public access
 *       (health, documentation) is decided explicitly once a real policy needs it.</li>
 *   <li><b>HTTP Basic remains the authentication mechanism.</b> It is the behaviour the
 *       service already had; JWT replaces or complements it in Step 13 without changing
 *       these authorization rules.</li>
 *   <li><b>No form login and no session-based login.</b> The generated login page that the
 *       default configuration served at {@code GET /login} is a browser flow HireFlow does
 *       not use — the API is consumed through explicit credentials per request.</li>
 *   <li><b>Stateless requests, CSRF disabled — deliberately.</b> Authentication rides on
 *       the {@code Authorization} header of every request (HTTP Basic today, bearer JWT in
 *       Step 13); authority is never stored in a cookie or server-side session, so there is
 *       no ambient browser credential a cross-site request could abuse — the only condition
 *       CSRF protects against. {@link SessionCreationPolicy#STATELESS} removes the one
 *       CSRF-relevant surface the defaults had (a {@code JSESSIONID} that could silently
 *       authenticate follow-up requests) instead of papering over it with tokens. The other
 *       REST services run without a security stack at all — no session, no cookies, no CSRF
 *       surface — so there is nothing to disable or preserve there; introducing security to
 *       those services happens together with JWT in Step 13.</li>
 *   <li><b>Security responses follow the Step 9 error contract.</b> 401 and 403 are written
 *       deliberately as {@code {timestamp, status, error, code, message, path}} JSON by
 *       {@link com.hireflow.auth.security.SecurityErrorResponseHandler} — no stack traces or
 *       internal details, 401 keeps its {@code WWW-Authenticate} challenge, and the response
 *       is written directly so no error dispatch can rewrite the status.</li>
 * </ul>
 *
 * <p>Uses the {@code SecurityFilterChain} bean model rather than the deprecated
 * {@code WebSecurityConfigurerAdapter} style. No custom authentication provider is
 * introduced: the auto-configured user store backs HTTP Basic exactly as before.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Builds the single filter chain guarding every request to auth-service.
     *
     * @param http Spring Security's {@code HttpSecurity} builder, injected by the container
     * @return the security filter chain for this service
     * @throws Exception when the security rules cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            SecurityErrorResponseHandler securityErrorResponseHandler) throws Exception {
        return http
                // No server-side session: authentication is derived fresh from the
                // Authorization header on every request, so nothing can be replayed
                // through a session cookie.
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF only guards cookie/session authenticated flows. This API never
                // stores authority in a cookie or session (see class javadoc), so a CSRF
                // token would add ceremony without protecting anything. Deliberate policy
                // for a stateless, header-authenticated REST service.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest()
                        .authenticated())
                // Authentication and access-denied responses are rendered deliberately in
                // the Step 9 error contract instead of Spring Security's empty defaults.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler))
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
