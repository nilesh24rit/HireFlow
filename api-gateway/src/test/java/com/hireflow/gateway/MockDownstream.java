package com.hireflow.gateway;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Minimal in-JVM downstream used by the gateway tests.
 *
 * <p>Each instance stands in for one HireFlow service on an ephemeral port. It echoes back
 * which service answered together with the HTTP method, path, query string and body it
 * received, which lets the tests prove that the gateway selected the right target and
 * forwarded the request unchanged. A path segment {@code /error/<code>} makes the mock
 * respond with that status code so downstream status pass-through can be asserted, and a
 * path segment {@code /security/401} makes it emit a deliberate auth-service style 401
 * (Step 9 error contract plus {@code WWW-Authenticate}) so security-response pass-through
 * can be asserted.</p>
 */
final class MockDownstream {

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

        int status = downstreamStatus(path);
        String body = status == 200
                ? "service=" + serviceName
                        + "|method=" + exchange.getRequestMethod()
                        + "|path=" + path
                        + "|query=" + (query != null ? query : "")
                        + "|body=" + requestBody
                : "downstream-error " + status;

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    /**
     * Emulates the deliberate 401 of auth-service: the Step 9 error contract with the HTTP
     * Basic challenge, so that security-response pass-through can be asserted.
     */
    private void respondWithSecurityRejection(HttpExchange exchange, String path) throws IOException {
        String body = "{\"timestamp\":\"2026-01-01T00:00:00Z\",\"status\":401,\"error\":\"Unauthorized\","
                + "\"code\":\"UNAUTHENTICATED\",\"message\":\"Authentication required\",\"path\":\"" + path + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("WWW-Authenticate",
                "Basic realm=\"Mock auth-service\", charset=\"UTF-8\"");
        exchange.sendResponseHeaders(401, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
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
