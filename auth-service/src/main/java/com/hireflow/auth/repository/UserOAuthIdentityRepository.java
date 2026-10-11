package com.hireflow.auth.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hireflow.auth.entity.OAuth2Provider;
import com.hireflow.auth.entity.UserOAuthIdentity;

/**
 * Persistence for linked external identities.
 *
 * <p>Lookups are always by the verified pair {@code (provider, providerSubject)} — the
 * provider's stable subject identifier — never by email address. Email-based identity
 * matching is deliberately unsupported by this repository so that no code path can
 * accidentally link an external identity to an existing account purely because the email
 * addresses happen to match.</p>
 */
public interface UserOAuthIdentityRepository extends JpaRepository<UserOAuthIdentity, UUID> {

    /**
     * Finds the identity row for a verified provider subject.
     *
     * @param provider        the external provider
     * @param providerSubject the provider's stable subject identifier
     * @return the linked identity, when this provider identity is already linked
     */
    Optional<UserOAuthIdentity> findByProviderAndProviderSubject(
            OAuth2Provider provider, String providerSubject);

    /**
     * Checks whether a verified provider subject is already linked to any user.
     *
     * @param provider        the external provider
     * @param providerSubject the provider's stable subject identifier
     * @return {@code true} when the identity is already linked
     */
    boolean existsByProviderAndProviderSubject(OAuth2Provider provider, String providerSubject);
}
