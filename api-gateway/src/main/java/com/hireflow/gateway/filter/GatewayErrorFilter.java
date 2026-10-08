package com.hireflow.gateway.filter;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Converts gateway-level upstream failures into a small, generic error response.
 *
 * <p>Only transport problems are handled here: a connection that cannot be established
 * yields {@code 502 Bad Gateway} and an upstream timeout yields {@code 504 Gateway Timeout}.
 * Responses that come back from a downstream service — including 400, 404, 409 and 500 —
 * are never intercepted, so service contracts pass through the gateway unchanged and no
 * domain error model is duplicated.</p>
 *
 * <p>The response body carries only the status and a fixed generic message. Stack traces,
 * target addresses, credentials and configuration details are never written to the client;
 * the detailed cause is written to the server log only.</p>
 */
@Component
public class GatewayErrorFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorFilter.class);

    private static final Set<String> TIMEOUT_EXCEPTIONS = Set.of(
            "java.util.concurrent.TimeoutException",
            "io.netty.handler.timeout.TimeoutException",
            "io.netty.handler.timeout.ReadTimeoutException",
            "io.netty.handler.timeout.WriteTimeoutException");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .onErrorResume(
                        ex -> !exchange.getResponse().isCommitted() && resolveStatus(ex) != null,
                        ex -> respond(exchange, ex));
    }

    private Mono<Void> respond(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status = resolveStatus(ex);
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        log.warn("route={} method={} path={} upstream failure: {} -> {}",
                route != null ? route.getId() : "unmatched",
                exchange.getRequest().getMethod(),
                exchange.getRequest().getPath().pathWithinApplication().value(),
                ex.getClass().getName(),
                status.value());

        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body = "{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + messageFor(status) + "\"}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private String messageFor(HttpStatus status) {
        if (status == HttpStatus.GATEWAY_TIMEOUT) {
            return "Upstream service timed out";
        }
        return "Upstream service is unavailable";
    }

    /**
     * Maps transport failures to a gateway status and returns {@code null} for every other
     * throwable, which is then left to the default handling of the framework.
     */
    private HttpStatus resolveStatus(Throwable ex) {
        Throwable current = ex;
        int depth = 0;
        while (current != null && depth < 20) {
            if (current instanceof ConnectException
                    || current instanceof UnknownHostException
                    || current instanceof NoRouteToHostException
                    || current instanceof PortUnreachableException) {
                return HttpStatus.BAD_GATEWAY;
            }
            if (TIMEOUT_EXCEPTIONS.contains(current.getClass().getName())) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
            depth++;
        }
        return null;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
