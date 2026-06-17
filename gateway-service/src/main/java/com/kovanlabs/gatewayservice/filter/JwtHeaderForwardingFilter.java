package com.kovanlabs.gatewayservice.filter;

import java.util.List;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

@Component
public class JwtHeaderForwardingFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(auth -> auth instanceof JwtAuthenticationToken)
                .cast(JwtAuthenticationToken.class)
                .map(jwtAuth -> {

                    Jwt jwt = jwtAuth.getToken();

                    String userId = jwt.getClaimAsString("userId");

                    String emailClaim = jwt.getClaimAsString("email");
                    final String email =
                            (emailClaim != null && !emailClaim.isBlank())
                                    ? emailClaim
                                    : jwt.getSubject();

                    List<String> roles = jwt.getClaimAsStringList("roles");
                    String role = (roles != null && !roles.isEmpty())
                            ? roles.get(0)
                            : "DEV";

                    List<String> services = jwt.getClaimAsStringList("services");
                    String servicesStr = (services != null && !services.isEmpty())
                            ? String.join(",", services)
                            : "";

                    ServerHttpRequest request = new ServerHttpRequestDecorator(exchange.getRequest()) {
                        private HttpHeaders cachedHeaders;

                        @Override
                        public HttpHeaders getHeaders() {
                            if (cachedHeaders == null) {
                                HttpHeaders headers = new HttpHeaders();
                                headers.putAll(super.getHeaders());
                                headers.remove("X-User-Id");
                                headers.remove("X-User-Email");
                                headers.remove("X-User-Role");
                                headers.remove("X-User-Services");

                                headers.add("X-User-Id", userId == null ? "" : userId);
                                headers.add("X-User-Email", email == null ? "" : email);
                                headers.add("X-User-Role", role);
                                headers.add("X-User-Services", servicesStr);
                                cachedHeaders = HttpHeaders.readOnlyHttpHeaders(headers);
                            }
                            return cachedHeaders;
                        }
                    };

                    return exchange.mutate()
                            .request(request)
                            .build();
                })
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
    }

    @Override
    public int getOrder() {
        return -100;
    }
}