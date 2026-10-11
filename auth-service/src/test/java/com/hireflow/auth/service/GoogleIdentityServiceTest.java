package com.hireflow.auth.service;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.hireflow.auth.dto.GoogleIdentity;
import com.hireflow.auth.entity.OAuth2Provider;
import com.hireflow.auth.entity.User;
import com.hireflow.auth.entity.UserOAuthIdentity;
import com.hireflow.auth.entity.UserRole;
import com.hireflow.auth.exception.OAuth2IdentityException;
import com.hireflow.auth.exception.OAuth2IdentityException.Reason;
import com.hireflow.auth.repository.UserOAuthIdentityRepository;
import com.hireflow.auth.repository.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Google identity → HireFlow user mapping, the security-critical core of
 * Google sign-in. Every interaction with Google is mocked; these tests assert the decisions
 * the service makes about identity:
 *
 * <ul>
 *   <li>a verified, previously-linked Google subject resolves to its user, and a disabled
 *       account is refused;</li>
 *   <li>a new Google subject registers a user with the default role and no password, and
 *       links the identity in the same call (so a single transaction covers both);</li>
 *   <li>an unlinked subject whose email matches an existing account is refused outright —
 *       never silently linked — which is the account-takeover guard;</li>
 *   <li>a lost (provider, subject) uniqueness race degrades into a clean conflict rather
 *       than a duplicate account;</li>
 *   <li>authenticated linking attaches a Google identity to an account the caller already
 *       owns, and refuses an identity owned by someone else.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class GoogleIdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserOAuthIdentityRepository identityRepository;

    private GoogleIdentityService service;

    @BeforeEach
    void setUp() {
        service = new GoogleIdentityService(userRepository, identityRepository);
    }

    private GoogleIdentity identity(String subject, String email) {
        return new GoogleIdentity(subject, email, "Given", "Family");
    }

    /**
     * Builds a detached user with the given id. The entity has no id setter (JPA populates
     * it), so the test assigns it reflectively to simulate a persisted record.
     */
    private User user(UUID id, String email, boolean enabled) {
        User user = new User();
        assignId(user, id);
        user.setEmail(email);
        user.setFirstName("Given");
        user.setLastName("Family");
        user.setRole(UserRole.CANDIDATE);
        user.setEnabled(enabled);
        return user;
    }

    private static void assignId(User user, UUID id) {
        try {
            var field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Test could not assign the user id", ex);
        }
    }

    // ------------------------------------------------------------------
    // Returning linked Google user
    // ------------------------------------------------------------------

    @Test
    void returnsLinkedUserForKnownGoogleSubject() {
        UUID userId = UUID.randomUUID();
        User existing = user(userId, "jane@example.com", true);
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-1"))
                .thenReturn(Optional.of(new UserOAuthIdentity(userId, OAuth2Provider.GOOGLE, "sub-1")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        User resolved = service.authenticateWithGoogle(identity("sub-1", "jane@example.com"));

        assertThat(resolved).isSameAs(existing);
        verify(userRepository, never()).saveAndFlush(any());
        verify(identityRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesDisabledLinkedAccount() {
        UUID userId = UUID.randomUUID();
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-disabled"))
                .thenReturn(Optional.of(new UserOAuthIdentity(userId, OAuth2Provider.GOOGLE, "sub-disabled")));
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user(userId, "disabled@example.com", false)));

        assertThatThrownBy(() -> service.authenticateWithGoogle(identity("sub-disabled", "disabled@example.com")))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.ACCOUNT_DISABLED);
    }

    // ------------------------------------------------------------------
    // New Google user registration
    // ------------------------------------------------------------------

    @Test
    void registersNewUserWithDefaultRoleAndNoPassword() {
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-new"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(identityRepository.saveAndFlush(any(UserOAuthIdentity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        User created = service.authenticateWithGoogle(identity("sub-new", "new@example.com"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("new@example.com");
        // Google sign-up can never grant elevated privileges: the role is fixed server-side.
        assertThat(savedUser.getRole()).isEqualTo(UserRole.CANDIDATE);
        // A Google-only account has no password and can never password-login.
        assertThat(savedUser.getPasswordHash()).isNull();
        assertThat(savedUser.isEnabled()).isTrue();
        assertThat(created.getEmail()).isEqualTo("new@example.com");

        ArgumentCaptor<UserOAuthIdentity> identityCaptor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(identityRepository).saveAndFlush(identityCaptor.capture());
        assertThat(identityCaptor.getValue().getProvider()).isEqualTo(OAuth2Provider.GOOGLE);
        assertThat(identityCaptor.getValue().getProviderSubject()).isEqualTo("sub-new");
        assertThat(identityCaptor.getValue().getUserId()).isEqualTo(savedUser.getId());
    }

    @Test
    void fallsBackToEmailLocalPartWhenGoogleSuppliesNoName() {
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-noname"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("alex@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(identityRepository.saveAndFlush(any(UserOAuthIdentity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.authenticateWithGoogle(new GoogleIdentity("sub-noname", "alex@example.com", "", ""));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        assertThat(userCaptor.getValue().getFirstName()).isEqualTo("alex");
        assertThat(userCaptor.getValue().getLastName()).isEmpty();
    }

    @Test
    void duplicateRegistrationRaceBecomesCleanConflict() {
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-race"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("race@example.com")).thenReturn(false);
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        // The concurrent sign-in won the (provider, subject) uniqueness contest.
        when(identityRepository.saveAndFlush(any(UserOAuthIdentity.class)))
                .thenThrow(new DataIntegrityViolationException("uk_user_oauth_identities_provider_subject"));

        assertThatThrownBy(() -> service.authenticateWithGoogle(identity("sub-race", "race@example.com")))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.IDENTITY_ALREADY_LINKED);
    }

    // ------------------------------------------------------------------
    // Account-takeover guard: never link on email match alone
    // ------------------------------------------------------------------

    @Test
    void refusesToAutoLinkWhenEmailMatchesExistingAccount() {
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-conflict"))
                .thenReturn(Optional.empty());
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.authenticateWithGoogle(identity("sub-conflict", "taken@example.com")))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.EMAIL_ALREADY_REGISTERED);

        // The refusal must not create a user or link anything.
        verify(userRepository, never()).saveAndFlush(any());
        verify(identityRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------
    // Authenticated linking (both password and Google)
    // ------------------------------------------------------------------

    @Test
    void linksGoogleIdentityToAuthenticatedUser() {
        User account = user(UUID.randomUUID(), "jane@example.com", true);
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-link"))
                .thenReturn(Optional.empty());
        when(identityRepository.saveAndFlush(any(UserOAuthIdentity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        User result = service.linkIdentity(account, identity("sub-link", "jane@example.com"));

        assertThat(result).isSameAs(account);
        ArgumentCaptor<UserOAuthIdentity> captor = ArgumentCaptor.forClass(UserOAuthIdentity.class);
        verify(identityRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(account.getId());
        assertThat(captor.getValue().getProviderSubject()).isEqualTo("sub-link");
    }

    @Test
    void linkingIsIdempotentForTheSameAccount() {
        User account = user(UUID.randomUUID(), "jane@example.com", true);
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-same"))
                .thenReturn(Optional.of(new UserOAuthIdentity(account.getId(), OAuth2Provider.GOOGLE, "sub-same")));

        User result = service.linkIdentity(account, identity("sub-same", "jane@example.com"));

        assertThat(result).isSameAs(account);
        verify(identityRepository, never()).saveAndFlush(any());
    }

    @Test
    void refusesToLinkAnIdentityOwnedByAnotherUser() {
        User account = user(UUID.randomUUID(), "jane@example.com", true);
        UUID otherUserId = UUID.randomUUID();
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-owned"))
                .thenReturn(Optional.of(new UserOAuthIdentity(otherUserId, OAuth2Provider.GOOGLE, "sub-owned")));

        assertThatThrownBy(() -> service.linkIdentity(account, identity("sub-owned", "jane@example.com")))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.IDENTITY_ALREADY_LINKED);

        verify(identityRepository, never()).saveAndFlush(any());
    }

    // ------------------------------------------------------------------
    // Duplicate prevention under concurrency
    // ------------------------------------------------------------------

    @Test
    void sameSubjectIsNeverRegisteredTwice() {
        // Two authentications for the same subject: the first registers, the second (after
        // the identity exists) resolves to the same user rather than creating another.
        UUID userId = UUID.randomUUID();
        User existing = user(userId, "jane@example.com", true);
        when(identityRepository.findByProviderAndProviderSubject(OAuth2Provider.GOOGLE, "sub-twice"))
                .thenReturn(
                        Optional.of(new UserOAuthIdentity(userId, OAuth2Provider.GOOGLE, "sub-twice")),
                        Optional.of(new UserOAuthIdentity(userId, OAuth2Provider.GOOGLE, "sub-twice")));
        when(userRepository.findById(userId)).thenReturn(Optional.of(existing));

        User first = service.authenticateWithGoogle(identity("sub-twice", "jane@example.com"));
        User second = service.authenticateWithGoogle(identity("sub-twice", "jane@example.com"));

        assertThat(first).isSameAs(existing);
        assertThat(second).isSameAs(existing);
        verify(userRepository, times(0)).saveAndFlush(any());
    }
}
