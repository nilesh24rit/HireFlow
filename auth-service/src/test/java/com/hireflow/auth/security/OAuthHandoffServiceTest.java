package com.hireflow.auth.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the properties that make a handoff code safe to hand to a browser after a
 * Google sign-in: unpredictable, short-lived, redeemable exactly once, and safe under
 * concurrent replay.
 *
 * <p>The token behind a handoff is a real {@link IssuedAccessToken} value, so these tests
 * exercise the genuine mapping rather than a stand-in.</p>
 */
class OAuthHandoffServiceTest {

    /** 32 bytes of entropy encode to 43 base64url characters. */
    private static final Pattern UNPREDICTABLE_CODE = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final OAuthHandoffService service = new OAuthHandoffService(60);

    private IssuedAccessToken token() {
        return new IssuedAccessToken("signed-token-" + UUID.randomUUID(),
                Instant.now().plusSeconds(3600));
    }

    @Test
    void issuedCodeRedeemsToTheTokenItGuards() {
        UUID userId = UUID.randomUUID();
        IssuedAccessToken accessToken = token();

        String code = service.issue(userId, accessToken);

        assertThat(code).matches(UNPREDICTABLE_CODE);
        OAuthHandoffService.Handoff handoff = service.redeem(code).orElseThrow();
        assertThat(handoff.userId()).isEqualTo(userId);
        assertThat(handoff.accessToken()).isSameAs(accessToken);
    }

    @Test
    void everyIssuedCodeIsDistinctEvenForTheSameToken() {
        UUID userId = UUID.randomUUID();
        IssuedAccessToken accessToken = token();

        Set<String> codes = Set.of(
                service.issue(userId, accessToken),
                service.issue(userId, accessToken),
                service.issue(userId, accessToken));

        // Codes are freshly random, never derived from the token or the user id, so the
        // same sign-in repeated cannot be predicted from a previously observed code.
        assertThat(codes).hasSize(3);
    }

    @Test
    void aCodeCanBeRedeemedOnlyOnce() {
        String code = service.issue(UUID.randomUUID(), token());

        assertThat(service.redeem(code)).isPresent();

        // Redemption deletes, so a replay within the TTL finds nothing at all.
        assertThat(service.redeem(code)).isEmpty();
        assertThat(service.redeem(code)).isEmpty();
    }

    @Test
    void anExpiredCodeIsRefused() throws Exception {
        OAuthHandoffService shortLived = new OAuthHandoffService(1);
        String code = shortLived.issue(UUID.randomUUID(), token());

        Thread.sleep(1_100);

        assertThat(shortLived.redeem(code)).isEmpty();
    }

    @Test
    void aNonPositiveLifetimeIsRefusedAtStartup() {
        assertThatThrownBy(() -> new OAuthHandoffService(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hireflow.oauth.handoff-ttl-seconds");
        assertThatThrownBy(() -> new OAuthHandoffService(-5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void blankAndMissingCodesAreRefusedWithoutTouchingTheStore() {
        String real = service.issue(UUID.randomUUID(), token());

        assertThat(service.redeem(null)).isEmpty();
        assertThat(service.redeem("")).isEmpty();
        assertThat(service.redeem("   ")).isEmpty();

        // A blank code must not consume a real one.
        assertThat(service.redeem(real)).isPresent();
    }

    @Test
    void aSurroundingWhitespaceIsToleratedSoCopyPasteStillWorks() {
        String code = service.issue(UUID.randomUUID(), token());

        assertThat(service.redeem("  " + code + "\n")).isPresent();
        assertThat(service.redeem(code)).isEmpty();
    }

    @Test
    void concurrentReplaysYieldTheTokenToExactlyOneCaller() throws Exception {
        String code = service.issue(UUID.randomUUID(), token());
        int callers = 16;

        ExecutorService pool = Executors.newFixedThreadPool(callers);
        try {
            CountDownLatch startLine = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                results.add(pool.submit(() -> {
                    startLine.await();
                    return service.redeem(code).isPresent();
                }));
            }
            startLine.countDown();

            int winners = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    winners++;
                }
            }
            // Whatever the interleaving, exactly one caller receives the token.
            assertThat(winners).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void theConfiguredLifetimeIsReportedToClients() {
        assertThat(service.ttl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(new OAuthHandoffService(90).ttl()).isEqualTo(Duration.ofSeconds(90));
    }
}
