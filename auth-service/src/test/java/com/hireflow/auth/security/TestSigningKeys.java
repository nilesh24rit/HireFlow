package com.hireflow.auth.security;

/**
 * Dedicated test-only JWT signing keys.
 *
 * <p>These values exist for tests alone. They are deliberately obviously-named so they
 * can never be mistaken for production credentials, and no production configuration ever
 * references this class.</p>
 */
public final class TestSigningKeys {

    /** Primary test key; long enough for HS256. Never use outside tests. */
    public static final String VALID = "unit-test-only-signing-key-do-not-use-in-production-0001";

    /** A different test key, simulating another deployment's signing configuration. */
    public static final String OTHER = "unit-test-only-signing-key-do-not-use-in-production-0002";

    /** Too short for HS256; used to prove startup validation. */
    public static final String TOO_SHORT = "short";

    private TestSigningKeys() {
    }
}
