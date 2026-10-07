package cidrz.assessment.backend.auth;

import cidrz.assessment.backend.exception.ForbiddenException;
import cidrz.assessment.backend.exception.ResourceNotFoundException;
import cidrz.assessment.backend.facility.entity.Facility;
import cidrz.assessment.backend.facility.repository.FacilityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Every access decision lives here, so the rules can be read and tested in one place instead of
 * being spread across controllers.
 *
 * <p>The scope always comes from the principal — that is, from the token — and never from the
 * request. A caller cannot widen what they can see by changing a query parameter.
 */
@Component
@RequiredArgsConstructor
public class AccessPolicy {

    private final FacilityRepository facilityRepository;

    /**
     * Resolves the facility if the principal may read it: 404 if it does not exist, 403 if it does
     * but is out of scope.
     */
    public Mono<Facility> authorizeRead(CareLinkPrincipal principal, String facilityId) {
        if (principal.role() == Role.CLINICIAN && !principal.facilityId().equals(facilityId)) {
            // Short-circuit: a clinician's own facility is known from the token, so this needs no query.
            return Mono.error(forbidden(facilityId));
        }
        return facilityRepository.findByIdAndIsDeletedFalse(facilityId)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Facility", facilityId)))
                .flatMap(facility -> canRead(principal, facility)
                        ? Mono.just(facility)
                        : Mono.error(forbidden(facilityId)));
    }

    /** Only clinicians write. Checked before the request body is read. */
    public void requireWriteRole(CareLinkPrincipal principal) {
        if (principal.role() != Role.CLINICIAN) {
            throw new ForbiddenException(
                    "Your role can view follow-up lists but cannot record contact attempts");
        }
    }

    /** And only for patients at their own facility. */
    public boolean canWritePatientAt(CareLinkPrincipal principal, String patientFacilityId) {
        return principal.role() == Role.CLINICIAN && principal.facilityId().equals(patientFacilityId);
    }

    private static boolean canRead(CareLinkPrincipal principal, Facility facility) {
        return switch (principal.role()) {
            case CLINICIAN -> principal.facilityId().equals(facility.getId());
            case DISTRICT_OFFICER -> principal.district().equals(facility.getDistrict());
        };
    }

    private static ForbiddenException forbidden(String facilityId) {
        return new ForbiddenException("You do not have access to facility " + facilityId);
    }
}
