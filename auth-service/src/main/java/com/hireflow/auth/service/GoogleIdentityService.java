package com.hireflow.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hireflow.auth.dto.GoogleIdentity;
import com.hireflow.auth.entity.OAuth2Provider;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserOAuthIdentity;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.exception.OAuth2IdentityException;
import com.hireflow.auth.exception.OAuth2IdentityException.Reason;
import com.hireflow.auth.repository.UserOAuthIdentityRepository;
import com.hireflow.auth.repository.UserRepository;

/**
 * Turns a verified Google identity into an authenticated HireFlow user.
 *
 * <p>This is the single place that decides, for a proven Google account, which HireFlow user
 * (if any) it maps onto. It never trusts an email address to prove identity — the Google
 * subject identifier ({@code sub}) is the only key the account mapping is built on — and it
 * never merges accounts implicitly.</p>
 *
 * <p>The outcomes, in order:</p>
 *
 * <ul>
 *   <li><b>Returning Google user.</b> When the subject is already linked, the owning user is
 *       loaded and returned after an enabled check. Disabled accounts are refused with
 *       {@link Reason#ACCOUNT_DISABLED}.</li>
 *   <li><b>Existing email conflict.</b> When the subject is not linked yet but the verified
 *       email matches an existing HireFlow account, sign-in is refused with
 *       {@link Reason#EMAIL_ALREADY_REGISTERED}. Linking the accounts must happen from an
 *       authenticated session via {@link #linkIdentity(User, GoogleIdentity)} so a Google
 *       login can never silently take over a password account — matching emails alone are
 *       not proof that two identities belong to the same person.</li>
 *   <li><b>New Google user.</b> Otherwise a HireFlow user is created with the default
 *       registration role ({@link UserRole#CANDIDATE} — never one the caller can choose) and
 *       no password hash, and the Google identity is linked to it. Both writes share one
 *       transaction, so a failure while linking rolls back the user too: no partially
 *       registered accounts.</li>
 * </ul>
 *
 * <p>This service issues no tokens and knows nothing about HTTP: it returns the user, and
 * the caller (the OAuth2 success handler) generates the same HireFlow JWT that password
 * login uses. Google access and refresh tokens are never read, stored or logged here.</p>
 */
@Service
public class GoogleIdentityService {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdentityService.class);

    private final UserRepository userRepository;
    private final UserOAuthIdentityRepository identityRepository;

    public GoogleIdentityService(UserRepository userRepository,
            UserOAuthIdentityRepository identityRepository) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
    }

    /**
     * Resolves a verified Google identity to the HireFlow user it belongs to, registering a
     * new user when the identity has never been seen before.
     *
     * @param identity the verified Google identity
     * @return the HireFlow user this Google account authenticates
     * @throws OAuth2IdentityException when the identity cannot be mapped safely — a disabled
     *                                 account, an unlinked identity whose email already
     *                                 belongs to someone else, or a race that lost the
     *                                 uniqueness contest
     */
    @Transactional
    public User authenticateWithGoogle(GoogleIdentity identity) {
        UserOAuthIdentity linked = identityRepository
                .findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, identity.subject())
                .orElse(null);

        if (linked != null) {
            return loadEnabledUser(linked.getUserId());
        }

        // The subject is not linked. An email that matches an existing account is a
        // conflict, not a licence to link: refuse and point at authenticated linking.
        if (userRepository.existsByEmail(identity.email())) {
            log.info("Refusing to auto-link Google identity: email matches an existing HireFlow account");
            throw new OAuth2IdentityException(Reason.EMAIL_ALREADY_REGISTERED);
        }

        return registerNewGoogleUser(identity);
    }

    /**
     * Links a verified Google identity to an account the caller has already authenticated.
     * This is the secure path for users who legitimately want both password and Google
     * sign-in: because the caller proves ownership of the HireFlow account first, linking a
     * Google identity to it cannot take anything over.
     *
     * @param user     the already-authenticated HireFlow user
     * @param identity the verified Google identity to link
     * @return the user, after linking
     * @throws OAuth2IdentityException when the identity is already linked to a different
     *                                 user
     */
    @Transactional
    public User linkIdentity(User user, GoogleIdentity identity) {
        UserOAuthIdentity existing = identityRepository
                .findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, identity.subject())
                .orElse(null);

        if (existing != null) {
            if (existing.getUserId().equals(user.getId())) {
                // Already linked to this very account; linking is idempotent.
                return user;
            }
            throw new OAuth2IdentityException(Reason.IDENTITY_ALREADY_LINKED);
        }

        identityRepository.saveAndFlush(
                new UserOAuthIdentity(user.getId(), OAuth2Provider.GOOGLE, identity.subject()));
        log.debug("Linked Google identity to existing HireFlow user");
        return user;
    }

    private User loadEnabledUser(java.util.UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException(
                        "Google identity is linked to a user that no longer exists"));
        if (!user.isEnabled()) {
            throw new OAuth2IdentityException(Reason.ACCOUNT_DISABLED);
        }
        return user;
    }

    private User registerNewGoogleUser(GoogleIdentity identity) {
        User user = new User();
        user.setEmail(identity.email());
        user.setFirstName(defaultFirstName(identity));
        user.setLastName(defaultLastName(identity));
        // New Google registrations always receive the default registration role. It is
        // fixed here — never taken from the provider or the client — so a Google sign-up
        // can never grant RECRUITER or ADMIN privileges.
        user.setRole(UserRole.CANDIDATE);
        // A Google-only account has no password, so it can never authenticate with one.
        user.setPasswordHash(null);

        User saved = userRepository.saveAndFlush(user);
        try {
            // Same transaction as the user insert: if linking fails, the user rolls back
            // too, so a failed sign-in leaves no half-created account behind.
            identityRepository.saveAndFlush(
                    new UserOAuthIdentity(saved.getId(), OAuth2Provider.GOOGLE, identity.subject()));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent sign-in with the same Google identity won the (provider, subject)
            // uniqueness race. The transaction rolls back both rows; the caller surfaces a
            // clean conflict instead of a duplicate account.
            log.info("Google identity was linked concurrently; rolling back duplicate registration");
            throw new OAuth2IdentityException(Reason.IDENTITY_ALREADY_LINKED, ex);
        }
        log.info("Registered a new HireFlow user from Google sign-in");
        return saved;
    }

    /** Google may supply no profile name; fall back to the email local part. */
    private String defaultFirstName(GoogleIdentity identity) {
        if (!identity.givenName().isBlank()) {
            return identity.givenName();
        }
        String email = identity.email();
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private String defaultLastName(GoogleIdentity identity) {
        return identity.familyName().isBlank() ? "" : identity.familyName();
    }
}
