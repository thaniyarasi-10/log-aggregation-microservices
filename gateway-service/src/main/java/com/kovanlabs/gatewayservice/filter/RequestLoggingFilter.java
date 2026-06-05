package com.kovanlabs.gatewayservice.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import static net.logstash.logback.argument.StructuredArguments.keyValue;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            try {
                long duration = System.currentTimeMillis() - startTime;
                ServerHttpResponse response = exchange.getResponse();
                int statusCode = response.getStatusCode() != null ? response.getStatusCode().value() : 200;

                String level = "INFO";
                if (statusCode >= 500) {
                    level = "ERROR";
                } else if (statusCode >= 400) {
                    level = "WARN";
                }

                Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
                String serviceName = route != null ? route.getId() : "gateway-service";

                String message = String.format("Request: %s %s | Response: %d | Time: %dms", method, path, statusCode, duration);
                
                if ("ERROR".equals(level)) {
                    LOGGER.error(message,
                            keyValue("service", serviceName),
                            keyValue("endpoint", path),
                            keyValue("method", method),
                            keyValue("statusCode", statusCode),
                            keyValue("responseTime", (double) duration),
                            keyValue("level", level));
                } else if ("WARN".equals(level)) {
                    LOGGER.warn(message,
                            keyValue("service", serviceName),
                            keyValue("endpoint", path),
                            keyValue("method", method),
                            keyValue("statusCode", statusCode),
                            keyValue("responseTime", (double) duration),
                            keyValue("level", level));
                } else {
                    LOGGER.info(message,
                            keyValue("service", serviceName),
                            keyValue("endpoint", path),
                            keyValue("method", method),
                            keyValue("statusCode", statusCode),
                            keyValue("responseTime", (double) duration),
                            keyValue("level", level));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to log request in RequestLoggingFilter: {}", e.getMessage(), e);
            }
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
