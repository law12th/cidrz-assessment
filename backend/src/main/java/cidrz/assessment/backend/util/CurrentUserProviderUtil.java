package cidrz.assessment.backend.util;

import cidrz.assessment.backend.auth.CareLinkPrincipal;
import cidrz.assessment.backend.auth.StaticTokenAuthenticationFilter;
import cidrz.assessment.backend.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

/**
 * The one way to find out who is calling, as in the kwasila services. There it reads the JWT from
 * {@code ReactiveSecurityContextHolder}; here it reads the principal that
 * {@link StaticTokenAuthenticationFilter} put on the exchange. Controllers and services depend on
 * this rather than on the filter, so swapping the filter for Keycloak touches one class.
 */
@Component
public class CurrentUserProviderUtil {

    public CareLinkPrincipal getPrincipal(ServerWebExchange exchange) {
        CareLinkPrincipal principal = exchange.getAttribute(StaticTokenAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (principal == null) {
            // Only reachable if a route is ever mapped outside /api/. Fail closed.
            throw new UnauthorizedException();
        }
        return principal;
    }
}
