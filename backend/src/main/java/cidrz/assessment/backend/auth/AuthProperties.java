package cidrz.assessment.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Static token-to-principal mapping, which the brief permits for this slice ("a static token mapped
 * to a role and facility is acceptable if you say what you would do in production instead").
 *
 * <p>In production this class goes away. Tokens become OIDC access tokens from Keycloak, validated
 * by the Spring Security resource server exactly as in the kwasila services, with the role and the
 * scope read from claims rather than from configuration — see the README and A-architecture.md. The
 * rest of the code does not change, because everything downstream already works from
 * {@link CareLinkPrincipal} rather than from the token.
 *
 * <p>Grants are validated at startup: a misconfigured token fails the deployment rather than
 * silently granting the wrong scope while clinics are open.
 */
@ConfigurationProperties(prefix = "carelink.auth")
public record AuthProperties(Map<String, TokenGrant> tokens) {

    public AuthProperties {
        tokens = tokens == null ? Map.of() : Map.copyOf(tokens);
        tokens.forEach((token, grant) -> grant.validate());
    }

    public record TokenGrant(String subject, Role role, String facilityId, String district) {

        void validate() {
            if (subject == null || role == null) {
                throw new IllegalStateException("Token grant is missing subject or role");
            }
            if (role == Role.CLINICIAN && facilityId == null) {
                throw new IllegalStateException("A CLINICIAN grant needs a facility-id: " + subject);
            }
            if (role == Role.DISTRICT_OFFICER && district == null) {
                throw new IllegalStateException("A DISTRICT_OFFICER grant needs a district: " + subject);
            }
        }

        CareLinkPrincipal toPrincipal() {
            return new CareLinkPrincipal(subject, role, facilityId, district);
        }
    }
}
