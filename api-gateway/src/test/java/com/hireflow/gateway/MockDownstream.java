package com.hireflow.gateway;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal in-JVM downstream used by the gateway tests.
 *
 * <p>Each instance stands in for one HireFlow service on an ephemeral port. It echoes back
 * which service answered together with the HTTP method, path, query string, forwarded
 * headers and body it received, which lets the tests prove that the gateway selected the
 * right target and forwarded the request unchanged. A path segment {@code /error/<code>}
 * makes the mock respond with that status code so downstream status pass-through can be
 * asserted, a path segment {@code /security/401} makes it emit a deliberate auth-service
 * style 401 (the common error contract plus {@code WWW-Authenticate}) so security-response
 * pass-through can be asserted, and a path under {@code /oauth2/authorization/} makes it
 * answer with the 302 redirect to the identity provider that auth-service returns, so the
 * browser redirect leg of Google sign-in can be asserted. The {@code Authorization} header
 * of every request is echoed back, so the tests can prove bearer tokens cross the gateway
 * verbatim.</p>
 */
final class MockDownstream {

    /** Headers the gateway is expected to describe the public hop with. */
    private static final List<String> FORWARD_HEADERS =
            List.of("X-Forwarded-For", "X-Forwarded-Host", "X-Forwarded-Port",
                    "X-Forwarded-Proto", "Forwarded");

    private final HttpServer server;
    private final String serviceName;

    private MockDownstream(HttpServer server, String serviceName) {
        this.server = server;
        this.serviceName = serviceName;
    }

    static MockDownstream start(String serviceName) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            MockDownstream downstream = new MockDownstream(server, serviceName);
            server.createContext("/", downstream::handle);
            server.start();
            return downstream;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to start mock downstream " + serviceName, ex);
        }
    }

    String uri() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getQuery();
        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        if (path.contains("/security/401")) {
            respondWithSecurityRejection(exchange, path);
            return;
        }

        if (path.contains("/security/403")) {
            respondWithForbiddenRejection(exchange, path);
            return;
        }

        if (path.startsWith("/oauth2/authorization/")) {
            respondWithIdentityProviderRedirect(exchange, path);
            return;
        }

        int status = downstreamStatus(path);
        String body = status == 200
                ? "service=" + serviceName
                        + "|method=" + exchange.getRequestMethod()
                        + "|path=" + path
                        + "|query=" + (query != null ? query : "")
                        + "|authorization=" + authorization(exchange)
                        + "|forwarded=" + forwardedHeaders(exchange)
                        + "|body=" + requestBody
                : "downstream-error " + status;

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * Emulates the 302 auth-service answers at the OAuth2 entry point: a redirect to the
     * identity provider, with the callback URL already pointing back at the public origin.
     * Lets the tests assert the gateway relays a redirect untouched.
     */
    private void respondWithIdentityProviderRedirect(HttpExchange exchange, String path)
            throws IOException {
        String provider = path.substring(path.lastIndexOf('/') + 1);
        exchange.getResponseHeaders().set("Location",
                "https://accounts.google.com/o/oauth2/v2/auth"
                        + "?client_id=unit-test-only"
                        + "&redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2F"
                        + provider
                        + "&response_type=code&scope=openid"
                        + "&state=unit-test-state");
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    /**
     * Emulates the deliberate 401 of auth-service: the common error contract with the
     * {@code Bearer} challenge — {@code error="invalid_token"} when a token was
     * supplied and rejected — so that security-response pass-through can be asserted.
     */
    private void respondWithSecurityRejection(HttpExchange exchange, String path) throws IOException {
        boolean invalidToken = exchange.getRequestHeaders().getFirst("Authorization") != null;
        String message = invalidToken
                ? "Invalid or expired authentication token"
                : "Authentication required";
        String challenge = invalidToken
                ? "Bearer realm=\"Mock auth-service\", error=\"invalid_token\""
                : "Bearer realm=\"Mock auth-service\"";
        String body = "{\"timestamp\":\"2026-01-01T00:00:00Z\",\"status\":401,\"error\":\"Unauthorized\","
                + "\"code\":\"UNAUTHENTICATED\",\"message\":\"" + message + "\",\"path\":\"" + path + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("WWW-Authenticate", challenge);
        exchange.sendResponseHeaders(401, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * Emulates the deliberate 403 of a downstream service: the common error contract
     * with code FORBIDDEN, so that authorization refusal pass-through can be asserted.
     */
    private void respondWithForbiddenRejection(HttpExchange exchange, String path) throws IOException {
        String body = "{\"timestamp\":\"2026-01-01T00:00:00Z\",\"status\":403,\"error\":\"Forbidden\","
                + "\"code\":\"FORBIDDEN\",\"message\":\"Access denied\",\"path\":\"" + path + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(403, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * The {@code Authorization} header exactly as the gateway forwarded it, or
     * {@code absent} when the client sent none. This is what lets the routing tests prove
     * that bearer tokens cross the gateway untouched.
     */
    private static String authorization(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null ? header : "absent";
    }

    /**
     * The forwarded-header set the gateway produced, so the tests can prove the hop is
     * described to the downstream while a value the client itself supplied is preserved
     * rather than silently trusted as the gateway's own.
     */
    private static String forwardedHeaders(HttpExchange exchange) {
        return FORWARD_HEADERS.stream()
                .map(name -> name + "=" + value(exchange, name))
                .collect(Collectors.joining(","));
    }

    private static String value(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst(name);
        return header != null ? header : "absent";
    }

    private static int downstreamStatus(String path) {
        String marker = "/error/";
        int index = path.indexOf(marker);
        if (index < 0) {
            return 200;
        }
        try {
            return Integer.parseInt(path.substring(index + marker.length()));
        } catch (NumberFormatException ex) {
            return 200;
        }
    }
}
