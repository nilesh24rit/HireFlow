-- Links an external OAuth2 identity (currently only Google) to a HireFlow user.
--
-- Design notes:
--   * One row per provider identity. `provider_subject` is the provider's stable, immutable
--     subject identifier (for Google, the `sub` claim) — never the email address, which a
--     provider may allow a user to change. Two different providers can never collide because
--     the uniqueness constraint spans (provider, provider_subject).
--   * `user_id` references the existing users table in the SAME database (this is an
--     internal, intra-service relationship — not a cross-service foreign key). ON DELETE
--     CASCADE keeps identities consistent when a user is removed.
--   * The (provider, provider_subject) uniqueness constraint is the database-level guarantee
--     that a single Google account can never be linked twice, even under a race between two
--     concurrent first-time sign-ins. The application checks first, and this constraint is
--     the final backstop.
--   * A user may have zero identities (password-only), exactly one, or — legitimately —
--     more than one (for example Google plus a future provider, or a password account that
--     also links Google). Email-based linking is deliberately NOT encoded here: identity rows
--     are only ever created after a verified provider subject is proven, never by matching
--     an email address.
CREATE TABLE user_oauth_identities (
    id UUID NOT NULL,
    user_id UUID NOT NULL,
    provider VARCHAR(20) NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pk_user_oauth_identities PRIMARY KEY (id),
    CONSTRAINT uk_user_oauth_identities_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT fk_user_oauth_identities_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
);

-- Supports fast "is this Google identity already linked?" lookups during sign-in.
CREATE INDEX ix_user_oauth_identities_user_id ON user_oauth_identities (user_id);
