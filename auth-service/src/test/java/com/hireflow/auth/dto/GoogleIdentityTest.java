package com.hireflow.auth.dto;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;

import com.hireflow.auth.exception.OAuth2IdentityException;
import com.hireflow.auth.exception.OAuth2IdentityException.Reason;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the safety checks that run before a Google identity can exist: the stable subject
 * and a verified, present email are mandatory, the email is normalised, and profile names are
 * optional. These are the claims the account mapping depends on, so a failure here must refuse
 * sign-in rather than guess an identity.
 */
class GoogleIdentityTest {

    private Map<String, Object> verifiedClaims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put(StandardClaimNames.SUB, "google-sub-123");
        claims.put(StandardClaimNames.EMAIL, "Jane.Doe@Example.com");
        claims.put(StandardClaimNames.EMAIL_VERIFIED, Boolean.TRUE);
        claims.put(StandardClaimNames.GIVEN_NAME, "Jane");
        claims.put(StandardClaimNames.FAMILY_NAME, "Doe");
        return claims;
    }

    @Test
    void buildsIdentityFromVerifiedClaims() {
        GoogleIdentity identity = GoogleIdentity.fromClaims(verifiedClaims());

        assertThat(identity.subject()).isEqualTo("google-sub-123");
        // Email is normalised so the same address always compares equal.
        assertThat(identity.email()).isEqualTo("jane.doe@example.com");
        assertThat(identity.givenName()).isEqualTo("Jane");
        assertThat(identity.familyName()).isEqualTo("Doe");
    }

    @Test
    void normalisesEmailToLowercase() {
        GoogleIdentity identity = GoogleIdentity.fromClaims(verifiedClaims());
        assertThat(identity.email()).isEqualTo(identity.email().toLowerCase());
    }

    @Test
    void acceptsVerifiedEmailAsBooleanOrString() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaimNames.EMAIL_VERIFIED, "true");
        assertThat(GoogleIdentity.fromClaims(claims).email()).isEqualTo("jane.doe@example.com");
    }

    @Test
    void treatsMissingProfileNamesAsEmpty() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(StandardClaimNames.GIVEN_NAME);
        claims.remove(StandardClaimNames.FAMILY_NAME);

        GoogleIdentity identity = GoogleIdentity.fromClaims(claims);
        assertThat(identity.givenName()).isEmpty();
        assertThat(identity.familyName()).isEmpty();
    }

    @Test
    void refusesMissingSubject() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(StandardClaimNames.SUB);

        assertThatThrownBy(() -> GoogleIdentity.fromClaims(claims))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.MISSING_SUBJECT);
    }

    @Test
    void refusesMissingEmail() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(StandardClaimNames.EMAIL);

        assertThatThrownBy(() -> GoogleIdentity.fromClaims(claims))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.MISSING_EMAIL);
    }

    @Test
    void refusesUnverifiedEmail() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaimNames.EMAIL_VERIFIED, Boolean.FALSE);

        assertThatThrownBy(() -> GoogleIdentity.fromClaims(claims))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.EMAIL_NOT_VERIFIED);
    }

    @Test
    void refusesUnverifiedEmailWhenClaimAbsent() {
        Map<String, Object> claims = verifiedClaims();
        claims.remove(StandardClaimNames.EMAIL_VERIFIED);

        assertThatThrownBy(() -> GoogleIdentity.fromClaims(claims))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.EMAIL_NOT_VERIFIED);
    }

    @Test
    void refusesBlankSubject() {
        Map<String, Object> claims = verifiedClaims();
        claims.put(StandardClaimNames.SUB, "   ");

        assertThatThrownBy(() -> GoogleIdentity.fromClaims(claims))
                .isInstanceOf(OAuth2IdentityException.class)
                .extracting(ex -> ((OAuth2IdentityException) ex).reason())
                .isEqualTo(Reason.MISSING_SUBJECT);
    }
}
