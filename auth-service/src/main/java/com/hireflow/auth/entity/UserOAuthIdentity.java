package com.hireflow.auth.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * A verified external identity (for example a Google account) linked to a HireFlow user.
 *
 * <p>The row is created only after the provider has authenticated the identity and the
 * service has decided it belongs to a specific user — it is a record of a proven linkage,
 * never a guess. The pair {@code (provider, providerSubject)} is unique across the whole
 * table, so one Google account can map to at most one HireFlow user no matter how many
 * sign-ins race to create it. {@code providerSubject} stores the provider's immutable
 * subject identifier (Google's {@code sub}), not an email address, which the provider may
 * let a user change.</p>
 *
 * <p>A user with no rows here simply has no linked provider — a password-only account. A
 * user with one (or, later, several) rows can additionally sign in through that provider.
 * Nothing here stores credentials, tokens, emails or profile data: only the mapping.</p>
 */
@Entity
@Table(name = "user_oauth_identities",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_oauth_identities_provider_subject",
                columnNames = { "provider", "provider_subject" }))
public class UserOAuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OAuth2Provider provider;

    /**
     * The provider's stable subject identifier (for Google, the {@code sub} claim). Unique
     * together with {@link #provider}; never an email address.
     */
    @Column(name = "provider_subject", nullable = false, length = 255)
    private String providerSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** JPA constructor; not intended for application use. */
    protected UserOAuthIdentity() {
    }

    /**
     * Links the given provider identity to the given user.
     *
     * @param userId          the HireFlow user that owns this identity
     * @param provider        the external provider that authenticated the identity
     * @param providerSubject the provider's stable subject identifier
     */
    public UserOAuthIdentity(UUID userId, OAuth2Provider provider, String providerSubject) {
        this.userId = userId;
        this.provider = provider;
        this.providerSubject = providerSubject;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public OAuth2Provider getProvider() {
        return provider;
    }

    public String getProviderSubject() {
        return providerSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
