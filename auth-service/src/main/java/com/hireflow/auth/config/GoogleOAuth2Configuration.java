package com.hireflow.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

/**
 * Google OAuth2 client registration for Google sign-in.
 *
 * <p><b>External credentials only.</b> The Google client id and secret are read exclusively
 * from the environment ({@code GOOGLE_CLIENT_ID} and {@code GOOGLE_CLIENT_SECRET}, or their
 * relaxed-binding property equivalents such as
 * {@code spring.security.oauth2.client.registration.google.client-id}). Nothing in this class
 * contains a real credential, and no configuration file is committed: when the environment
 * does not supply the credentials, the {@link ClientRegistrationRepository} bean is not
 * created at all (see {@link GoogleOAuth2EnabledCondition}) and the service starts with Google
 * sign-in disabled — never with a placeholder or default credential.</p>
 *
 * <p><b>Standard endpoints.</b> The registration carries Google's well-known public
 * authorization, token, JWK-set, user-info and issuer URIs plus the standard {@code openid},
 * {@code profile} and {@code email} scopes and {@code sub} as the username attribute. The
 * authorization-code exchange, state validation and OIDC ID-token verification are performed
 * entirely by Spring Security's OAuth2 client — nothing is hand-rolled here.</p>
 *
 * <p><b>Redirect URI behind the gateway.</b> By default Spring resolves the callback to
 * {@code {baseUrl}/login/oauth2/code/google}, where {@code baseUrl} is the host the service
 * itself was reached on. When auth-service sits behind the API gateway, the operator can pin
 * the public callback with {@code HIREFLOW_OAUTH_REDIRECT_URI} (for example
 * {@code http://localhost:8080}), so Google sends the browser back through the gateway. That
 * value is trusted operator configuration, not a client-supplied forwarded header, so it
 * cannot be spoofed by a request.</p>
 *
 * <p>Registration id {@value #GOOGLE_REGISTRATION_ID} produces the standard entry point
 * {@code GET /oauth2/authorization/google} and callback
 * {@code GET /login/oauth2/code/google} handled by Spring Security.</p>
 */
@Configuration(proxyBeanMethods = false)
public class GoogleOAuth2Configuration {

    /** Registration id; fixes the entry-point and callback paths of Google sign-in. */
    public static final String GOOGLE_REGISTRATION_ID = "google";

    /** Environment property supplying the Google OAuth2 client id. Never logged or exposed. */
    public static final String CLIENT_ID_PROPERTY = "GOOGLE_CLIENT_ID";

    /** Environment property supplying the Google OAuth2 client secret. Never logged or exposed. */
    public static final String CLIENT_SECRET_PROPERTY = "GOOGLE_CLIENT_SECRET";

    /**
     * Optional operator-supplied public base URL (scheme and host, no trailing path) used to
     * build the redirect URI when the service is reached through the API gateway.
     */
    public static final String REDIRECT_BASE_URL_PROPERTY = "HIREFLOW_OAUTH_REDIRECT_URI";

    /** Callback path template appended to the operator-supplied redirect base URL. */
    public static final String REDIRECT_URI_TEMPLATE = "/login/oauth2/code/" + GOOGLE_REGISTRATION_ID;

    /**
     * Default redirect URI template. {@code {baseUrl}} resolves to whatever host the service
     * itself was reached on, which is correct when auth-service is called directly. Behind
     * the API gateway the operator overrides it with {@link #REDIRECT_BASE_URL_PROPERTY}.
     */
    public static final String DEFAULT_REDIRECT_URI = "{baseUrl}" + REDIRECT_URI_TEMPLATE;

    // Google's public, documented OIDC endpoint URLs — not secrets, fixed by the provider.
    static final String GOOGLE_ISSUER_URI = "https://accounts.google.com";
    static final String GOOGLE_AUTHORIZATION_URI = "https://accounts.google.com/o/oauth2/v2/auth";
    static final String GOOGLE_TOKEN_URI = "https://www.googleapis.com/oauth2/v4/token";
    static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";
    static final String GOOGLE_USER_INFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    /**
     * The externally supplied Google client credentials. The secret is masked in
     * {@link #toString()} so it can never surface in logs through incidental formatting.
     */
    record GoogleOAuth2Properties(String clientId, String clientSecret, String redirectBaseUrl) {

        @Override
        public String toString() {
            // Deliberately omits both credentials: properties objects end up in logs.
            return "GoogleOAuth2Properties[clientId configured=" + StringUtils.hasText(clientId)
                    + ", clientSecret configured=" + StringUtils.hasText(clientSecret)
                    + ", redirectBaseUrl configured=" + StringUtils.hasText(redirectBaseUrl) + "]";
        }
    }

    /**
     * Reads the Google credentials from the environment on every startup. Credentials are
     * never written to disk by this project and never have a hardcoded fallback.
     *
     * @param environment the Spring environment (system environment and property sources)
     * @return the resolved properties; values may be absent when sign-in is not configured
     */
    @Bean
    GoogleOAuth2Properties googleOAuth2Properties(Environment environment) {
        return new GoogleOAuth2Properties(
                environment.getProperty(CLIENT_ID_PROPERTY),
                environment.getProperty(CLIENT_SECRET_PROPERTY),
                environment.getProperty(REDIRECT_BASE_URL_PROPERTY));
    }

    /**
     * Registers the Google provider only when the environment actually supplies both
     * credentials. Without them this nested configuration is skipped entirely, so the
     * service boots cleanly with the OAuth2 endpoints absent rather than misconfigured.
     */
    @Configuration(proxyBeanMethods = false)
    @Conditional(GoogleOAuth2EnabledCondition.class)
    static class GoogleOAuth2ClientConfiguration {

        /**
         * Builds the single Google {@link ClientRegistration} from the external credentials.
         *
         * @param properties the externally supplied Google credentials
         * @return an in-memory repository holding exactly the Google registration
         */
        @Bean
        ClientRegistrationRepository clientRegistrationRepository(GoogleOAuth2Properties properties) {
            ClientRegistration.Builder builder = ClientRegistration
                    .withRegistrationId(GOOGLE_REGISTRATION_ID)
                    .clientId(properties.clientId())
                    .clientSecret(properties.clientSecret())
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .issuerUri(GOOGLE_ISSUER_URI)
                    .authorizationUri(GOOGLE_AUTHORIZATION_URI)
                    .tokenUri(GOOGLE_TOKEN_URI)
                    .jwkSetUri(GOOGLE_JWK_SET_URI)
                    .userInfoUri(GOOGLE_USER_INFO_URI)
                    .userNameAttributeName(IdTokenClaimNames.SUB)
                    .scope(OidcScopes.OPENID, OidcScopes.PROFILE, OidcScopes.EMAIL)
                    .clientName("Google")
                    // The manual builder has no provider preset, so the callback template
                    // is set explicitly. {baseUrl} resolves to the host auth-service was
                    // reached on unless the operator pins a public base URL below.
                    .redirectUri(DEFAULT_REDIRECT_URI);
            if (StringUtils.hasText(properties.redirectBaseUrl())) {
                builder.redirectUri(properties.redirectBaseUrl().replaceAll("/$", "") + REDIRECT_URI_TEMPLATE);
            }
            return new InMemoryClientRegistrationRepository(builder.build());
        }
    }

    /**
     * Matches only when both Google credentials are present in the environment. A
     * half-configured state (id without secret) counts as not configured: a registration
     * with a missing secret would fail at the token exchange instead of failing fast here.
     */
    static class GoogleOAuth2EnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return StringUtils.hasText(environment.getProperty(CLIENT_ID_PROPERTY))
                    && StringUtils.hasText(environment.getProperty(CLIENT_SECRET_PROPERTY));
        }
    }
}
