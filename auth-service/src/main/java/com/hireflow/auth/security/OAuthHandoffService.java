package com.hireflow.auth.security;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The secure completion step of Google sign-in: a one-time, short-lived code that stands in
 * for the access token while the browser callback finishes.
 *
 * <p><b>Why a handoff exists at all.</b> The OAuth2 callback is a browser navigation, and
 * putting the HireFlow JWT into a redirect URL — query or fragment — would leak it into
 * browser history, proxy and access logs, and the {@code Referer} header of whatever page
 * the user lands on. Instead the success handler mints the token with the same
 * {@link JwtService} password login uses, hands it to this service, and returns only an
 * opaque code. The client then redeems that code once, over {@code POST
 * /api/auth/oauth2/token}, to receive the real {@code LoginResponse}.</p>
 *
 * <p><b>Why the code is safe to expose.</b> It behaves like a bearer credential only for a
 * moment and only once:</p>
 *
 * <ul>
 *   <li><b>Unpredictable.</b> Each code is 32 bytes from a dedicated {@link SecureRandom},
 *       base64url encoded — 256 bits of entropy, so it cannot be guessed or enumerated.</li>
 *   <li><b>Short-lived.</b> A code expires {@code hireflow.oauth.handoff-ttl-seconds}
 *       (default {@value #DEFAULT_TTL_SECONDS} seconds) after issue, bounding how long an
 *       intercepted code is worth anything.</li>
 *   <li><b>Single-use.</b> {@link #redeem(String)} removes the entry with an atomic
 *       {@link ConcurrentHashMap#remove(Object)}, so exactly one caller ever receives the
 *       token; a concurrent replay races and loses.</li>
 *   <li><b>Replay-protected.</b> Because redemption deletes, a second attempt with the same
 *       code finds nothing and fails — the code cannot be redeemed twice, even within its
 *       TTL.</li>
 *   <li><b>Not a login-CSRF vector.</b> The code is handed out in the body of the callback
 *       response, never carried in a URL, so an attacker cannot plant one in a link the
 *       victim's browser will follow. Login CSRF on the redirect itself is already defeated
 *       by the OAuth2 {@code state} parameter that Spring Security validates before the
 *       success handler runs.</li>
 * </ul>
 *
 * <p><b>Contents.</b> An entry maps the code to the user id and the already-signed token.
 * It never stores or logs the token: the token lives only inside the record handed back by
 * a single successful redemption. Codes are never logged either — they are treated as
 * credentials, exactly like the tokens they guard.</p>
 *
 * <p><b>Memory.</b> Pending entries live in a bounded in-process map. They are purged on
 * every issue, and issuance refuses once {@value #MAX_PENDING_HANDOFFS} entries are
 * outstanding, so an unbounded amount of state can never accumulate. This store is
 * deliberately per-instance: a handoff is redeemed by the same service instance that
 * issued it within seconds, so nothing needs to be shared or persisted, and a lost entry
 * simply costs the user one retry rather than any security property.</p>
 */
@Service
public class OAuthHandoffService {

    /** Entropy of a handoff code; 32 bytes is 256 bits, comfortably unguessable. */
    static final int CODE_BYTES = 32;

    /** Default lifetime of a handoff code, in seconds. */
    static final String DEFAULT_TTL_SECONDS = "60";

    /** Outstanding codes allowed before issuance is refused; bounds the in-memory map. */
    static final int MAX_PENDING_HANDOFFS = 10_000;

    private final Map<String, Handoff> pending = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();
    private final Duration ttl;

    /**
     * A single outstanding handoff: the user it authenticates, the token it guards and the
     * instant it stops being redeemable.
     *
     * @param userId      the HireFlow user the token belongs to
     * @param accessToken the signed token a successful redemption returns
     * @param expiresAt   when this handoff stops being redeemable
     */
    public record Handoff(UUID userId, IssuedAccessToken accessToken, Instant expiresAt) {

        /** True once the handoff is past its deliberately short lifetime. */
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    public OAuthHandoffService(
            @Value("${hireflow.oauth.handoff-ttl-seconds:" + DEFAULT_TTL_SECONDS + "}") long ttlSeconds) {
        if (ttlSeconds <= 0) {
            throw new IllegalStateException(
                    "hireflow.oauth.handoff-ttl-seconds must be a positive number of seconds but was "
                            + ttlSeconds);
        }
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    /**
     * Issues a fresh single-use code standing in for the given token.
     *
     * @param userId      the user the token authenticates
     * @param accessToken the signed token the code guards
     * @return the one-time code, safe to return to the client
     * @throws IllegalStateException when too many handoffs are already outstanding
     */
    public String issue(UUID userId, IssuedAccessToken accessToken) {
        purgeExpired();
        if (pending.size() >= MAX_PENDING_HANDOFFS) {
            throw new IllegalStateException(
                    "Too many pending OAuth2 handoffs; refusing to issue another one");
        }

        byte[] buffer = new byte[CODE_BYTES];
        secureRandom.nextBytes(buffer);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
        pending.put(code, new Handoff(userId, accessToken, Instant.now().plus(ttl)));
        return code;
    }

    /**
     * Redeems a one-time code for the token it guards.
     *
     * <p>The entry is removed atomically before it is inspected, so a code yields its token
     * to exactly one caller: any second attempt — a replay, a race, or the same code
     * resubmitted moments later — finds nothing and is refused.</p>
     *
     * @param code the one-time code supplied by the client
     * @return the outstanding handoff, or empty when the code is unknown, blank, already
     *         redeemed or expired
     */
    public Optional<Handoff> redeem(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        Handoff handoff = pending.remove(code.trim());
        if (handoff == null || handoff.isExpired()) {
            return Optional.empty();
        }
        return Optional.of(handoff);
    }

    /** Configured lifetime of a handoff code, reported to clients as {@code expiresIn}. */
    public Duration ttl() {
        return ttl;
    }

    /** Drops handoffs that have outlived their short lifetime. */
    private void purgeExpired() {
        pending.values().removeIf(Handoff::isExpired);
    }
}
