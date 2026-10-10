package com.hireflow.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.hireflow.auth.security.JwtAuthenticationFilter;
import com.hireflow.auth.security.SecurityErrorResponseHandler;

/**
 * Explicit security configuration for auth-service.
 *
 * <p>Since Step 13 the authentication mechanism is stateless bearer JWT, replacing the
 * interim HTTP Basic of Step 12:
 *
 * <ul>
 *   <li><b>One public endpoint.</b> {@code POST /api/auth/login} is the only
 *       {@code permitAll} rule — it is how a caller exchanges credentials for a token.
 *       Every other endpoint requires authentication; there is no wildcard public access,
 *       so nothing can be exposed accidentally. Swagger ({@code /v3/api-docs},
 *       {@code /swagger-ui}) intentionally stays behind authentication, the policy Step 12
 *       established for this service.</li>
 *   <li><b>Bearer tokens are validated by {@link JwtAuthenticationFilter}.</b> It sits
 *       before {@link UsernamePasswordAuthenticationFilter}, accepts only
 *       {@code Authorization: Bearer <token>}, verifies the token with {@code JwtService}
 *       and populates the security context solely on success. Missing, malformed, expired
 *       or wrongly signed tokens continue unauthenticated and the authorization rules
 *       reject them on protected routes — an invalid token can never degrade into
 *       anonymous success.</li>
 *   <li><b>No form login, no sessions, CSRF disabled — deliberately.</b> Authority never
 *       lives in a cookie or server-side session: it is proven per request by the bearer
 *       token, the only condition CSRF protects against is therefore absent.
 *       {@link SessionCreationPolicy#STATELESS} removes the session surface entirely, and
 *       no {@code JSESSIONID} is ever issued (asserted over real HTTP by
 *       {@code AuthSecurityBaselineTest}).</li>
 *   <li><b>Security responses follow the Step 9 error contract.</b> 401 and 403 are written
 *       deliberately as {@code {timestamp, status, error, code, message, path}} JSON by
 *       {@link SecurityErrorResponseHandler}, with a {@code Bearer} challenge on 401 and
 *       no stack traces, claims or internal details anywhere.</li>
 * </ul>
 *
 * <p>Uses the {@code SecurityFilterChain} bean model rather than the deprecated
 * {@code WebSecurityConfigurerAdapter} style. Role-based endpoint rules belong to
 * Step 15; Google OAuth2 login to Step 14.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Builds the single filter chain guarding every request to auth-service.
     *
     * @param http Spring Security's {@code HttpSecurity} builder, injected by the container
     * @param jwtAuthenticationFilter the stateless bearer-token filter of this service
     * @param securityErrorResponseHandler the deliberate 401/403 responder
     * @return the security filter chain for this service
     * @throws Exception when the security rules cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
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
                        // Credential login is the single public endpoint: it is how a
                        // caller obtains the credentials for every protected endpoint.
                        .requestMatchers("/api/auth/login").permitAll()
                        .anyRequest()
                        .authenticated())
                // Authentication and access-denied responses are rendered deliberately in
                // the Step 9 error contract instead of Spring Security's empty defaults.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler))
                // Bearer-token authentication: validate the JWT, then populate the
                // security context. No Basic, no form login, no session store.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
