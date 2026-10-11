package com.hireflow.auth.entity;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * External identity provider whose users can sign in to HireFlow.
 *
 * <p>The value is persisted as the {@code provider} column of
 * {@code user_oauth_identities} and forms, together with the provider's stable subject
 * identifier, the unique key that maps an external identity onto exactly one HireFlow user.
 * Only providers this service actually integrates with are listed; an unknown provider can
 * never be stored because the column is enumerated.</p>
 */
@Schema(description = "External identity provider of a linked OAuth2 identity",
        enumAsRef = true,
        allowableValues = { "GOOGLE" })
public enum OAuth2Provider {

    /** Google sign-in; the subject identifier is the Google {@code sub} claim. */
    GOOGLE
}
