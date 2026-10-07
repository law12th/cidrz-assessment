package cidrz.assessment.backend.auth;

/**
 * Who is calling and what they are scoped to: a clinician has a {@code facilityId}, a district
 * officer has a {@code district}. Only this shape crosses into the service layer — never the raw
 * token — so no service can accidentally log or forward a credential.
 */
public record CareLinkPrincipal(String subject, Role role, String facilityId, String district) {
}
