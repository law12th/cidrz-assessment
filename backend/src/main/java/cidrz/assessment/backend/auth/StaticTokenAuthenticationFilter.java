package cidrz.assessment.backend.auth;

import cidrz.assessment.backend.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Authenticates every {@code /api} request from its bearer token; anything else under {@code /api}
 * gets a 401. Runs after the correlation ID filter, so even a 401 carries a correlation ID.
 *
 * <p>The kwasila services do this with {@code SecurityConfig} and a Spring Security filter chain
 * against Keycloak. A plain {@link WebFilter} is used here because the brief's static token is not
 * a JWT and there is no identity provider to validate it against; wiring the security starter around
 * a hard-coded map would add a framework without adding a check. The production replacement is the
 * kwasila {@code SecurityConfig} unchanged — see {@link AuthProperties}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class StaticTokenAuthenticationFilter implements WebFilter {

    public static final String PRINCIPAL_ATTRIBUTE = StaticTokenAuthenticationFilter.class.getName() + ".principal";

    private static final String BEARER = "Bearer ";
    private static final String API_PREFIX = "/api/";

    private final AuthProperties properties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!exchange.getRequest().getPath().value().startsWith(API_PREFIX)) {
            return chain.filter(exchange);
        }

        String header = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return Mono.error(new UnauthorizedException());
        }

        AuthProperties.TokenGrant grant = properties.tokens().get(header.substring(BEARER.length()).trim());
        if (grant == null) {
            // Deliberately the same response as a missing token: an unknown token and a malformed
            // one are not worth distinguishing for a caller, and distinguishing them helps a prober.
            return Mono.error(new UnauthorizedException());
        }

        exchange.getAttributes().put(PRINCIPAL_ATTRIBUTE, grant.toPrincipal());
        return chain.filter(exchange);
    }
}
