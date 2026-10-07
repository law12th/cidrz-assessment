package cidrz.assessment.backend.followup.service;

import cidrz.assessment.backend.auth.CareLinkPrincipal;
import cidrz.assessment.backend.common.PagedResponse;
import cidrz.assessment.backend.followup.dto.FollowUpDTO;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public interface FollowUpService {

    /**
     * The follow-up list for one facility.
     *
     * <p>Takes the raw parameters rather than a parsed query so that parsing, and the rejection of
     * anything invalid, sits behind the service boundary with the rest of the rules — the
     * controller stays a routing concern.
     */
    Mono<PagedResponse<FollowUpDTO.FollowUpItemResponse>> list(CareLinkPrincipal principal,
                                                               MultiValueMap<String, String> params);

    /**
     * Records a contact attempt against a patient. Safe to replay with the same Idempotency-Key.
     *
     * <p>The body arrives unresolved, as a {@code Mono}, so that the caller's role can be checked
     * before it is read.
     */
    Mono<Void> recordContact(CareLinkPrincipal principal,
                             String rawPatientId,
                             Mono<FollowUpDTO.ContactRequest> body,
                             String idempotencyKey);
}
