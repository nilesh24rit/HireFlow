package com.hireflow.auth.dto;

import java.util.Locale;
import java.util.Map;

import org.springframework.security.oauth2.core.oidc.StandardClaimNames;

import com.hireflow.auth.exception.OAuth2IdentityException;
import com.hireflow.auth.exception.OAuth2IdentityException.Reason;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The minimum, already-verified identity HireFlow needs from a Google account.
 *
 * <p>Instances are built only from claims the identity provider itself asserted after a
 * successful, signature-verified OpenID Connect exchange — never from anything the client
 * supplied directly. {@link #fromClaims(Map)} re-checks the safety properties this service
 * depends on before an instance can exist:</p>
 *
 * <ul>
 *   <li>the stable subject identifier ({@code sub}) must be present, because it — not the
 *       email — is the key the account mapping is built on. Google never changes {@code sub}
 *       for an account, whereas a user may change the email;</li>
 *   <li>the email must be present and marked {@code email_verified}; an unverified email is
 *       never used to identify or create a person;</li>
 *   <li>the email is normalised to lower case so the same address always compares equal.</li>
 * </ul>
 *
 * <p>Given and family names are optional profile data with no security meaning; they are
 * copied verbatim and default to empty when the account has none.</p>
 */
@Schema(description = "Verified Google identity of the authenticated user")
public record GoogleIdentity(
        @Schema(description = "Google's stable subject identifier (sub)", requiredMode = Schema.RequiredMode.REQUIRED)
        String subject,
        @Schema(description = "Verified email address", requiredMode = Schema.RequiredMode.REQUIRED)
        String email,
        @Schema(description = "Given name from the Google profile, may be empty")
        String givenName,
        @Schema(description = "Family name from the Google profile, may be empty")
        String familyName) {

    /**
     * Builds a validated identity from the claims asserted by Google.
     *
     * @param claims the verified claim set of the OpenID Connect identity
     * @return a validated identity safe to map onto a HireFlow user
     * @throws OAuth2IdentityException when a required claim is missing or the email is not
     *                                 verified; the reason encodes the deliberate refusal
     */
    public static GoogleIdentity fromClaims(Map<String, Object> claims) {
        String subject = text(claims.get(StandardClaimNames.SUB));
        if (subject == null) {
            throw new OAuth2IdentityException(Reason.MISSING_SUBJECT);
        }

        String email = text(claims.get(StandardClaimNames.EMAIL));
        if (email == null) {
            throw new OAuth2IdentityException(Reason.MISSING_EMAIL);
        }

        boolean emailVerified = Boolean.parseBoolean(text(claims.get(StandardClaimNames.EMAIL_VERIFIED)));
        if (!emailVerified) {
            throw new OAuth2IdentityException(Reason.EMAIL_NOT_VERIFIED);
        }

        return new GoogleIdentity(
                subject,
                email.toLowerCase(Locale.ROOT),
                emptyIfNull(text(claims.get(StandardClaimNames.GIVEN_NAME))),
                emptyIfNull(text(claims.get(StandardClaimNames.FAMILY_NAME))));
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static String emptyIfNull(String value) {
        return value != null ? value : "";
    }
}
