package com.hireflow.auth.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.hireflow.auth.security.JwtAuthenticationFilter;
import com.hireflow.auth.security.SecurityErrorResponseHandler;

/**
 * Explicit security configuration for auth-service.
 *
 * <p>The authentication mechanism is stateless bearer JWT, with Google OAuth2 login as an
 * additional way to obtain a token:</p>
 *
 * <ul>
 *   <li><b>Public endpoints are enumerated, never wildcarded.</b> {@code POST /api/auth/login}
 *       (credential login) plus the two standard OAuth2 endpoints — the entry point
 *       {@code GET /oauth2/authorization/google} and the callback
 *       {@code GET /login/oauth2/code/google} — are the only {@code permitAll} rules. Every
 *       other endpoint requires authentication; there is no wildcard public access, so
 *       nothing can be exposed accidentally. Swagger ({@code /v3/api-docs},
 *       {@code /swagger-ui}) intentionally stays behind authentication.</li>
 *   <li><b>Bearer tokens are validated by {@link JwtAuthenticationFilter}.</b> It sits
 *       before {@link UsernamePasswordAuthenticationFilter}, accepts only
 *       {@code Authorization: Bearer <token>}, verifies the token with {@code JwtService}
 *       and populates the security context solely on success. Missing, malformed, expired
 *       or wrongly signed tokens continue unauthenticated and the authorization rules
 *       reject them on protected routes — an invalid token can never degrade into
 *       anonymous success.</li>
 *   <li><b>Google OAuth2 login is enabled only when the Google credentials exist.</b> When
 *       {@code GOOGLE_CLIENT_ID} and {@code GOOGLE_CLIENT_SECRET} are supplied,
 *       {@link GoogleOAuth2Configuration} contributes the Google client registration and
 *       this chain adds {@code oauth2Login}; without them the service starts with the same
 *       chain and the OAuth2 endpoints simply do not exist. Both password and Google login
 *       produce the identical HireFlow JWT — there is one token format and one protected
 *       surface.</li>
 *   <li><b>The API stays session-free; the OAuth2 dance gets transient state only.</b> The
 *       authorization-code flow needs somewhere to keep its {@code state} (the CSRF
 *       protection of the browser redirect) between the entry point and the callback. That
 *       pending authorization request is held by Spring Security in a short-lived servlet
 *       session via {@link HttpSessionOAuth2AuthorizationRequestRepository}. Crucially the
 *       chain keeps {@link SessionCreationPolicy#STATELESS}: under that policy Spring
 *       Security never reads or writes the <i>authenticated principal</i> to a session, so
 *       the session created by the OAuth2 dance carries only the pending {@code state} and
 *       can never turn a bearer-token API into a session-authenticated one. Every API
 *       request is still authenticated purely from its {@code Authorization} header by
 *       {@link JwtAuthenticationFilter} (asserted over real HTTP by
 *       {@code AuthSecurityBaselineTest}, which observes no session cookie on API calls).</li>
 *   <li><b>No form login, CSRF disabled — deliberately.</b> Authority never lives in a
 *       cookie or server-side session: it is proven per request by the bearer token, the
 *       only condition CSRF protects against is therefore absent, and the OAuth2 state
 *       parameter covers the browser redirect flow.</li>
 *   <li><b>Security responses follow the common HireFlow error contract.</b> 401 and 403 are written
 *       deliberately as {@code {timestamp, status, error, code, message, path}} JSON by
 *       {@link SecurityErrorResponseHandler}, with a {@code Bearer} challenge on 401 and
 *       no stack traces, claims or internal details anywhere.</li>
 * </ul>
 *
 * <p>Uses the {@code SecurityFilterChain} bean model rather than the deprecated
 * {@code WebSecurityConfigurerAdapter} style.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    /**
     * Builds the single filter chain guarding every request to auth-service.
     *
     * @param http Spring Security's {@code HttpSecurity} builder, injected by the container
     * @param jwtAuthenticationFilter the stateless bearer-token filter of this service
     * @param securityErrorResponseHandler the deliberate 401/403 responder
     * @param clientRegistrationRepositories the OAuth2 client registrations contributed by
     *                                      {@link GoogleOAuth2Configuration}; absent when
     *                                      Google credentials are not configured
     * @return the security filter chain for this service
     * @throws Exception when the security rules cannot be built
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            SecurityErrorResponseHandler securityErrorResponseHandler,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositories) throws Exception {
        http
                // No server-side session holds authentication: every request is
                // authenticated fresh from the Authorization header. The OAuth2 flow below
                // uses a transient session only for its pending state, never for a
                // principal (see class javadoc).
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // CSRF only guards cookie/session authenticated flows. This API never
                // stores authority in a cookie or session (see class javadoc), so a CSRF
                // token would add ceremony without protecting anything. The OAuth2
                // authorization-code flow is protected by its state parameter instead.
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(authorize -> authorize
                        // Credential login is a public endpoint: it is how a caller
                        // obtains the credentials for every protected endpoint.
                        .requestMatchers("/api/auth/login").permitAll()
                        // The standard Spring Security OAuth2 endpoints. The entry point
                        // redirects the browser to Google; the callback receives Google's
                        // redirect. Both are safe to expose: the callback is protected by
                        // the OAuth2 state parameter, and no authority is granted on
                        // either path itself.
                        .requestMatchers("/oauth2/authorization/**", "/login/oauth2/code/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                // Authentication and access-denied responses are rendered deliberately in
                // the common HireFlow error contract instead of Spring Security's empty defaults.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorResponseHandler)
                        .accessDeniedHandler(securityErrorResponseHandler))
                // Bearer-token authentication: validate the JWT, then populate the
                // security context. No Basic, no form login, no session store.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Google OAuth2 login joins the chain only when the Google registration exists.
        // The pending authorization request (its state, the CSRF guard of the redirect) is
        // held in a short-lived servlet session, keeping the whole API session-free for
        // authentication purposes.
        if (clientRegistrationRepositories.getIfAvailable() != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .authorizationEndpoint(authorization -> authorization
                            .authorizationRequestRepository(
                                    new HttpSessionOAuth2AuthorizationRequestRepository())));
        }

        return http.build();
    }
}
