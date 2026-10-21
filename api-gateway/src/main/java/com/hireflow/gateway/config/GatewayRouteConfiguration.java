package com.hireflow.gateway.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Route table of the HireFlow API gateway.
 *
 * <p>Each entry names the route id, the public path predicate and the target service URI.
 * Paths are forwarded untouched because the downstream controllers already expose the same
 * {@code /api/...} contract, so no path rewriting filters are needed:</p>
 *
 * <pre>
 * auth-service        /api/users/**         -&gt; auth-service
 * candidate-service   /api/candidates/**    -&gt; candidate-service
 * job-service         /api/jobs/**          -&gt; job-service
 * application-service /api/applications/**  -&gt; application-service
 * google-oauth2       /oauth2/authorization/**, /login/oauth2/code/**,
 *                     /api/auth/oauth2/**   -&gt; auth-service
 * </pre>
 *
 * <p><b>The Google sign-in paths, and only those.</b> A browser-based OAuth2 flow cannot be
 * pointed at an internal address: the entry point that redirects to Google, the callback
 * Google redirects back to, and the endpoint that redeems the resulting handoff code must all
 * be reachable on the same public origin the user is looking at. They are added here as
 * their own route, leaving every existing predicate and target untouched. Nothing else about
 * authentication moves to the edge — in particular credential login keeps its existing
 * direct-to-service deployment.</p>
 *
 * <p><b>No forwarded-header trust, on either side.</b> The gateway strips caller-supplied
 * {@code X-Forwarded-*} and {@code Forwarded} headers rather than relaying them, and adds
 * none of its own, so no forwarded header anywhere in the flow can describe the public
 * origin. That is precisely why auth-service does not read one: its redirect URI is pinned
 * by the operator through {@code HIREFLOW_OAUTH_REDIRECT_URI} — trusted configuration, not
 * a request-supplied value — so a caller cannot steer the OAuth2 callback by forging a
 * header.</p>
 *
 * <p><b>Authentication.</b> Routes carry no JWT filter and no signing key: the
 * {@code Authorization} header is forwarded byte-for-byte, and the service behind the route
 * is the only component that validates a token. Duplicating validation here would give the
 * edge and the service two opinions about the same credential, and would force a secret to
 * exist in a module that never needs one. A rejected token therefore comes back through the
 * gateway as the downstream 401, unchanged. The OAuth2 flow is no exception: Google's
 * {@code code} and {@code state} parameters and the one-time handoff code are relayed, and
 * every decision about them is made in auth-service.</p>
 */
@Configuration
@EnableConfigurationProperties(HireFlowServiceUris.class)
public class GatewayRouteConfiguration {

    static final String AUTH_SERVICE_ROUTE_ID = "auth-service";
    static final String CANDIDATE_SERVICE_ROUTE_ID = "candidate-service";
    static final String JOB_SERVICE_ROUTE_ID = "job-service";
    static final String APPLICATION_SERVICE_ROUTE_ID = "application-service";
    static final String AI_SERVICE_ROUTE_ID = "ai-service";
    static final String GOOGLE_OAUTH2_ROUTE_ID = "google-oauth2";

    static final String USERS_PATH = "/api/users/**";
    static final String CANDIDATES_PATH = "/api/candidates/**";
    static final String JOBS_PATH = "/api/jobs/**";
    static final String APPLICATIONS_PATH = "/api/applications/**";
    static final String AI_PATH = "/api/ai/**";

    /** Browser entry point; auth-service answers with the redirect to Google. */
    static final String OAUTH2_ENTRY_POINT_PATH = "/oauth2/authorization/**";

    /** Where Google sends the browser back; carries the {@code code} and {@code state}. */
    static final String OAUTH2_CALLBACK_PATH = "/login/oauth2/code/**";

    /** Redeems the one-time handoff code of a completed sign-in for an access token. */
    static final String OAUTH2_HANDOFF_PATH = "/api/auth/oauth2/**";

    @Bean
    public RouteLocator apiRoutes(RouteLocatorBuilder builder, HireFlowServiceUris services) {
        return builder.routes()
                .route(AUTH_SERVICE_ROUTE_ID, route -> route
                        .path(USERS_PATH)
                        .uri(services.authServiceUri()))
                .route(CANDIDATE_SERVICE_ROUTE_ID, route -> route
                        .path(CANDIDATES_PATH)
                        .uri(services.candidateServiceUri()))
                .route(JOB_SERVICE_ROUTE_ID, route -> route
                        .path(JOBS_PATH)
                        .uri(services.jobServiceUri()))
                .route(APPLICATION_SERVICE_ROUTE_ID, route -> route
                        .path(APPLICATIONS_PATH)
                        .uri(services.applicationServiceUri()))
                .route(AI_SERVICE_ROUTE_ID, route -> route
                        .path(AI_PATH)
                        .uri(services.aiServiceUri()))
                // The three paths Google sign-in needs, and nothing else — all landing on
                // auth-service, which already owns identity and token issuance.
                .route(GOOGLE_OAUTH2_ROUTE_ID, route -> route
                        .path(OAUTH2_ENTRY_POINT_PATH, OAUTH2_CALLBACK_PATH, OAUTH2_HANDOFF_PATH)
                        .uri(services.authServiceUri()))
                .build();
    }
}
