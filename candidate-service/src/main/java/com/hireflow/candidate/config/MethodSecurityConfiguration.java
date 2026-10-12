package com.hireflow.candidate.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

/**
 * Enables Spring Security method-level authorization for candidate-service.
 *
 * <p>This is deliberately its own small class rather than part of
 * {@link SecurityConfiguration}: the filter chain decides <i>who may reach</i> candidate
 * endpoints (a valid bearer token is required), while method security decides <i>what an
 * authenticated caller may do</i> — which roles may manage profiles, and, together with
 * the persisted ownership check in {@code CandidateService}, whose profile they may
 * touch. Keeping the two apart leaves both readable and prevents the single chain class
 * from growing into one enormous configuration.</p>
 *
 * <p>{@code @EnableMethodSecurity} activates {@code @PreAuthorize} on Spring-managed
 * beans. The expressions match the fixed {@code ROLE_<role>} authorities derived from the
 * verified token's role claim — never from request input. A denied method throws
 * {@code AccessDeniedException}, which the {@code GlobalExceptionHandler} renders as the
 * common HireFlow 403 contract.</p>
 */
@Configuration
@EnableMethodSecurity
public class MethodSecurityConfiguration {
}
