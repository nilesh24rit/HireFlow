package com.hireflow.candidate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.hireflow.candidate.security.JwtAuthenticationFilter;
import com.hireflow.candidate.security.SecurityErrorResponseHandler;

/**
 * Explicit security configuration for candidate-service.
 *
 * <p>This service verifies bearer JWTs itself:
 *
 * <ul>
 *   <li><b>Business endpoints require authentication.</b> Every {@code /api/candidates/**}
 *       request must carry a valid {@code Authorization: Bearer <token>} issued by
 *       auth-service. There is no wildcard public access.</li>
 *   <li><b>API documentation stays public — deliberately.</b> {@code /v3/api-docs} and
 *       {@code /swagger-ui} remain unauthenticated, preserving this service's previous
 *       explicit policy so its contract can be browsed without credentials. The document
 *       itself carries no secrets.</li>
 *   <li><b>Bearer tokens are validated by {@link JwtAuthenticationFilter}.</b> It accepts
 *       only {@code Authorization: Bearer <token>}, verifies the token with
 *       {@link com.hireflow.candidate.security.JwtService} and populates the security
 *       context solely on success. Missing, malformed, expired or wrongly signed tokens
 *       continue unauthenticated and are rejected on protected routes by the authorization
 *       rules — an invalid token can never degrade into anonymous success.</li>
 *   <li><b>Stateless, CSRF disabled — deliberately.</b> Authority never lives in a cookie
 *       or session: it is proven per request by the bearer token, so there is no ambient
 *       browser credential a cross-site request could abuse. No session is created and no
 *       cookie is issued.</li>
 *   <li><b>Security responses follow the common HireFlow error contract.</b> 401 and 403 are
 *       rendered deliberately by {@link SecurityErrorResponseHandler} — same JSON shape as
 *       application errors, no stack traces, claims or internal details.</li>
 * </ul>
 *
 * <p>No role-based rules here.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            SecurityErrorResponseHandler securityErrorResponseHandler) throws Exception {
        return http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF only guards cookie/session authenticated flows; this API authenticates
                // per request through the Authorization header, so CSRF has nothing to guard.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        // Public by deliberate policy: browsable API documentation.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
