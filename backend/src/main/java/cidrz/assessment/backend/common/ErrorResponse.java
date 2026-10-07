package cidrz.assessment.backend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/**
 * The error shape fixed by the brief (appendix 15.2):
 * {@code { "error": { "code", "message", "correlation_id" } }}.
 *
 * <p>This is the one place the CareLink contract overrides the house style. The kwasila services
 * return a flat body with {@code errorCode}, {@code timestamp}, {@code path}, {@code fieldErrors}
 * and {@code traceId}; here the published contract wins, and the front end in {@code web/} parses
 * exactly this shape. {@code correlation_id} carries what {@code traceId} carries there, and is the
 * same value as the {@code X-Correlation-Id} response header.
 */
@Value
@Builder
@Schema(description = "Error response")
public class ErrorResponse {

    @Schema(description = "The failure", requiredMode = Schema.RequiredMode.REQUIRED)
    Body error;

    @Value
    @Builder
    @Schema(name = "ErrorDetail", description = "Error detail")
    public static class Body {

        @Schema(description = "Stable machine-readable code", example = "VALIDATION_FAILED")
        String code;

        @Schema(description = "Message safe to show a clinician", example = "facility_id is required")
        String message;

        @Schema(description = "Quote this when reporting a problem", example = "8c21-44f1")
        String correlationId;
    }
}
