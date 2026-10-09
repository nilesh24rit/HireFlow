package com.hireflow.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

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
 *   <li><b>CSRF is untouched at this checkpoint</b> and is decided deliberately (with tests)
 *       in the CSRF policy checkpoint of Step 12, based on the current authentication model.</li>
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
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest()
                        .authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
