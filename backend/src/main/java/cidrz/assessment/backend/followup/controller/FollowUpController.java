package cidrz.assessment.backend.followup.controller;

import cidrz.assessment.backend.common.ErrorResponse;
import cidrz.assessment.backend.common.PagedResponse;
import cidrz.assessment.backend.followup.dto.FollowUpDTO;
import cidrz.assessment.backend.followup.service.FollowUpService;
import cidrz.assessment.backend.util.CurrentUserProviderUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Thin by design: identify the caller, hand over, return. Every rule lives in the service, the
 * access policy and the rules class, where it can be tested without a web layer.
 *
 * <p>Mapped at {@code /api/follow-up}, not at the house {@code /api/v1/...}: the path is fixed by
 * the published contract in the brief (appendix 15.2) and by the front end in {@code web/}.
 * A-architecture.md describes versioning the national API under {@code /api/v1} once there is more
 * than one consumer to keep in step.
 */
@RestController
@RequestMapping(value = "/api/follow-up", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Follow-up", description = "Patients who have missed a follow-up appointment")
@ApiResponse(responseCode = "400", description = "The request was rejected",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "401", description = "No valid bearer token",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "403", description = "Out of the caller's scope",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
@ApiResponse(responseCode = "429", description = "Too many requests",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
public class FollowUpController {

    private final FollowUpService followUpService;
    private final CurrentUserProviderUtil currentUserProvider;

    @Operation(summary = "List patients due for follow-up at one facility",
            description = """
                    A patient is listed when their most recent visit set a next_appointment_date, \
                    that date is more than overdue_days in the past, and they have had no visit \
                    since. A clinician may read their own facility; a district officer may read \
                    any facility in their district.
                    """)
    @ApiResponse(responseCode = "200", description = "A page of the follow-up list")
    @ApiResponse(responseCode = "404", description = "No such facility",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    // Declared here rather than as method arguments because the handler reads the raw query map:
    // FollowUpQueryParser rejects unknown and repeated parameters, which @RequestParam cannot do --
    // Spring would bind the ones it recognises and silently discard the rest.
    @Parameters({
            @Parameter(name = "facility_id", in = ParameterIn.QUERY, required = true,
                    description = "Facility code", example = "FAC-0101"),
            @Parameter(name = "status", in = ParameterIn.QUERY,
                    description = "Omit for the default list: missed and overdue together",
                    schema = @Schema(allowableValues = {"due_soon", "overdue", "missed"})),
            @Parameter(name = "overdue_days", in = ParameterIn.QUERY,
                    description = "Days past the appointment before a patient is listed",
                    schema = @Schema(type = "integer", defaultValue = "7", minimum = "0", maximum = "365")),
            @Parameter(name = "sort", in = ParameterIn.QUERY,
                    schema = @Schema(allowableValues = {"days_overdue_desc", "days_overdue_asc"},
                            defaultValue = "days_overdue_desc")),
            @Parameter(name = "page", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "1", minimum = "1", maximum = "10000")),
            @Parameter(name = "page_size", in = ParameterIn.QUERY,
                    schema = @Schema(type = "integer", defaultValue = "50", minimum = "1", maximum = "200"))
    })
    @GetMapping
    public Mono<PagedResponse<FollowUpDTO.FollowUpItemResponse>> list(ServerWebExchange exchange) {

        return Mono.defer(() -> followUpService.list(
                currentUserProvider.getPrincipal(exchange),
                exchange.getRequest().getQueryParams()));
    }

    @Operation(summary = "Record a contact attempt against a patient",
            description = """
                    Clinicians only, and only for patients at their own facility. Supply an \
                    Idempotency-Key to make the call safe to replay after a timeout; the same key \
                    records the attempt exactly once.
                    """)
    @ApiResponse(responseCode = "204", description = "Recorded, or already recorded under this key")
    @ApiResponse(responseCode = "404", description = "No such patient at this facility",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @PostMapping(path = "/{id}/contacted", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Void>> recordContact(
            @Parameter(description = "Patient id") @PathVariable("id") String patientId,
            @Parameter(description = "Makes a replayed request safe", example = "replay-key-0001")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody Mono<FollowUpDTO.ContactRequest> body,
            ServerWebExchange exchange) {

        return Mono.defer(() -> followUpService.recordContact(
                        currentUserProvider.getPrincipal(exchange), patientId, body, idempotencyKey))
                .then(Mono.fromSupplier(() -> ResponseEntity.noContent().<Void>build()));
    }
}
