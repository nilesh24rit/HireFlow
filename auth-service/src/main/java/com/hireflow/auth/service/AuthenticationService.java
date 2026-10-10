package com.hireflow.auth.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.auth.dto.LoginRequest;
import com.hireflow.auth.dto.LoginResponse;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.exception.InvalidCredentialsException;
import com.hireflow.auth.repository.UserRepository;
import com.hireflow.auth.security.IssuedAccessToken;
import com.hireflow.auth.security.JwtService;

/**
 * Credential verification for email-and-password login.
 *
 * <p>The account is looked up by email and the submitted password is verified against
 * the stored hash through Spring Security's {@link PasswordEncoder}. Every failure mode —
 * unknown email, account without a password hash, wrong password, disabled account —
 * produces the same generic {@link InvalidCredentialsException}, so responses never
 * reveal whether an email address is registered.</p>
 *
 * <p>When the email is unknown, the same BCrypt verification still runs against a
 * throwaway hash generated at startup. That keeps the response timing of an unknown
 * email close to the timing of a wrong password, closing the cheapest user-enumeration
 * side channel. The throwaway hash is derived from a random value and is not a
 * credential for any account.</p>
 *
 * <p>The raw password is never logged and never leaves this class; token issuance is
 * handled by {@code JwtService} once credentials are verified.</p>
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    /** Message shared by every login failure so responses reveal nothing. */
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    /** RFC 6750 token type returned by a successful login. */
    private static final String BEARER_TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Hash of a random, unguessable value; verified against only to equalise timings. */
    private final String timingEqualisationHash;

    public AuthenticationService(UserRepository userRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.timingEqualisationHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Verifies the submitted credentials and returns the authenticated user.
     *
     * @param email       submitted email address
     * @param rawPassword submitted raw password; never stored or logged
     * @return the user owning the verified credentials
     * @throws InvalidCredentialsException with the same generic message for every failure
     */
    @Transactional(readOnly = true)
    public User verifyCredentials(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElse(null);
        String storedHash = user != null && user.getPasswordHash() != null
                ? user.getPasswordHash()
                : timingEqualisationHash;

        boolean passwordMatches = rawPassword != null && passwordEncoder.matches(rawPassword, storedHash);

        if (user == null || user.getPasswordHash() == null || !user.isEnabled() || !passwordMatches) {
            // Log the failure category only — never the email, password or hash.
            log.debug("Credential verification failed: {}", failureCategory(user, passwordMatches));
            throw new InvalidCredentialsException(INVALID_CREDENTIALS_MESSAGE);
        }
        return user;
    }

    /**
     * Exchanges verified credentials for a bearer access token.
     *
     * @param request login payload with email and password
     * @return the issued token, its type and remaining lifetime
     * @throws InvalidCredentialsException when the credentials cannot be verified
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = verifyCredentials(request.email(), request.password());
        IssuedAccessToken accessToken = jwtService.generateAccessToken(user.getId(), user.getRole());
        return new LoginResponse(accessToken.token(), BEARER_TOKEN_TYPE, accessToken.expiresInSeconds());
    }

    private String failureCategory(User user, boolean passwordMatches) {
        if (user == null) {
            return "unknown email";
        }
        if (user.getPasswordHash() == null) {
            return "account has no password set";
        }
        if (!user.isEnabled()) {
            return "account disabled";
        }
        return passwordMatches ? "unexpected state" : "wrong password";
    }
}
