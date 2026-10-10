package com.hireflow.job.security;

/**
 * Dedicated test-only JWT signing key.
 *
 * <p>This value exists for tests alone. It is deliberately obviously-named so it can never
 * be mistaken for a production credential, and no production configuration ever references
 * this class.</p>
 */
public final class TestSigningKeys {

    /** Primary test key; long enough for HS256. Never use outside tests. */
    public static final String VALID = "unit-test-only-signing-key-do-not-use-in-production-0001";

    /** A different test key, simulating a foreign deployment's signing configuration. */
    public static final String OTHER = "unit-test-only-signing-key-do-not-use-in-production-0002";

    private TestSigningKeys() {
    }
}