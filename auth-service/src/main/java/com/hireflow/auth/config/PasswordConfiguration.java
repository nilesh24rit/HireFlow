package com.hireflow.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Password hashing for credential login (Step 13).
 *
 * <p>A {@link BCryptPasswordEncoder} is declared as an explicit bean so every place that
 * stores or verifies a password — user registration and credential verification — uses
 * the same adaptive hash. BCrypt is deliberately salted per hash and cost-tuned, so raw
 * passwords are never persisted and hashes remain resistant to offline cracking. The
 * encoder is also picked up by Spring Security for any internally managed user details,
 * keeping encoding consistent across the service.</p>
 */
@Configuration
public class PasswordConfiguration {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
