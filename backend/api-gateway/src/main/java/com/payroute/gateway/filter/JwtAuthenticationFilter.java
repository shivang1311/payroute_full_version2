package com.payroute.gateway.filter;

import com.payroute.gateway.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    private final JwtUtil jwtUtil;
    private final List<String> openPaths;

    public JwtAuthenticationFilter(
            JwtUtil jwtUtil,
            @Value("${payroute.gateway.open-paths}") List<String> openPaths) {
        this.jwtUtil = jwtUtil;
        this.openPaths = openPaths;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Generate correlation ID for distributed tracing
        String correlationId = request.getHeaders().getFirst("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        // Allow open paths without authentication
        if (isOpenPath(path)) {
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-Correlation-Id", correlationId)
                    .build();
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }

        // Check for Swagger/API docs paths
        if (path.contains("/swagger-ui") || path.contains("/api-docs") || path.contains("/actuator")) {
            return chain.filter(exchange);
        }

        // Extract and validate JWT
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for path: {}", path);
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtUtil.validateToken(token);

            String userId = claims.getSubject();
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            Object partyIdClaim = claims.get("partyId");
            String partyId = partyIdClaim == null ? "" : String.valueOf(partyIdClaim);

            // Inject user context headers for downstream services
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Username", username != null ? username : "")
                    .header("X-User-Role", role != null ? role : "")
                    .header("X-Party-Id", partyId)
                    .header("X-Correlation-Id", correlationId)
                    .build();

            log.debug("Authenticated user {} (role: {}) for path: {}", username, role, path);

            return chain.filter(exchange.mutate().request(mutatedRequest).build());

        } catch (Exception e) {
            log.warn("JWT validation failed for path {}: {}", path, e.getMessage());
            return onError(exchange, HttpStatus.UNAUTHORIZED);
        }
    }

    private boolean isOpenPath(String path) {
        return openPaths.stream().anyMatch(openPath -> {
            if (openPath.endsWith("/**")) {
                return path.startsWith(openPath.substring(0, openPath.length() - 3));
            }
            return path.equals(openPath);
        });
    }

    private Mono<Void> onError(ServerWebExchange exchange, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        return response.setComplete();
    }

    @Override
    public int getOrder() {
        return -1; // Run before other filters
    }
}
