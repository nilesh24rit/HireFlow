package com.hireflow.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Access logging for every routed gateway request.
 *
 * <p>Only the route id, HTTP method, request path and response status are written. Query
 * strings, request and response headers, bodies, credentials and tokens are deliberately
 * never logged, so no sensitive data can reach the log files.</p>
 */
@Component
public class GatewayLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(GatewayLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return chain.filter(exchange)
                .doFinally(signal -> {
                    Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                    String status = exchange.getResponse().getStatusCode() != null
                            ? String.valueOf(exchange.getResponse().getStatusCode().value())
                            : "none";
                    log.info("route={} method={} path={} status={}",
                            route != null ? route.getId() : "unmatched",
                            exchange.getRequest().getMethod(),
                            exchange.getRequest().getPath().pathWithinApplication().value(),
                            status);
                });
    }

    @Override
    public int getOrder() {
        // Outermost filter, so the status written by the error filter is visible here.
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
