package cidrz.assessment.backend.followup.dto;

import cidrz.assessment.backend.followup.entity.type.FollowUpStatus;
import cidrz.assessment.backend.followup.entity.type.SortOrder;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Every shape that crosses the wire for the follow-up feature, nested in one interface as in the
 * kwasila services, so a reader sees the whole contract of the slice in a single file.
 */
public interface FollowUpDTO {

    /**
     * Validated query parameters for {@code GET /api/follow-up}, produced by
     * {@code FollowUpQueryParser}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class FollowUpQuery {

        private String facilityId;

        /** Null means the brief's default list: missed and overdue together. */
        private FollowUpStatus status;

        private int overdueDays;
        private SortOrder sort;
        private int page;
        private int pageSize;

        /** Rows to skip. Widened to long so a large page times a large page number cannot overflow. */
        public long offset() {
            return (long) (page - 1) * pageSize;
        }
    }

    /**
     * Body of {@code POST /api/follow-up/{id}/contacted}.
     *
     * <p>The timestamp is carried as a String rather than an {@code Instant} so that a badly
     * formatted value produces our own message ("contacted_at must be an ISO 8601 timestamp with an
     * offset") instead of a Jackson parse failure, which would be a 400 whose body quotes the
     * rejected input back at the caller.
     *
     * <p>The constraint annotations document the contract and drive the generated OpenAPI schema.
     * They are deliberately <em>not</em> enforced with {@code @Valid} on the controller method, as
     * they would be in a kwasila service: Spring validates a request body before the handler runs,
     * which would turn a district officer's malformed write into 400 "invalid body" instead of 403
     * "your role cannot write". The role must be decided first, whatever the body contains, so the
     * checks run in the service after {@code AccessPolicy.requireWriteRole}.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "A recorded attempt to contact a patient")
    class ContactRequest {

        @NotBlank
        @Schema(description = "When the contact happened, ISO 8601 with an offset",
                example = "2026-10-06T09:14:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        private String contactedAt;

        @Size(max = 500)
        @Schema(description = "Optional free-text note", example = "Phoned, will come Friday")
        private String note;
    }

    /**
     * One patient on the list, in the contract's shape (serialised in snake_case).
     *
     * <p>For {@code due_soon} rows {@code missedAppointmentDate} holds the <em>upcoming</em>
     * appointment and {@code daysOverdue} is zero or negative. The field keeps the contract's name
     * even though "missed" is then inaccurate, because renaming it would break the published
     * contract; the README records the ambiguity.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "A patient on a follow-up list")
    class FollowUpItemResponse {

        @Schema(example = "p_8841")
        private String id;

        @Schema(example = "0101-000471")
        private String patientNumber;

        @Schema(example = "Thandiwe")
        private String firstName;

        @Schema(example = "Banda")
        private String lastName;

        @Schema(example = "FAC-0101")
        private String facilityId;

        @Schema(example = "Mwansa Urban Clinic")
        private String facilityName;

        @Schema(description = "The appointment that was missed; for due_soon rows, the next one",
                example = "2026-09-05")
        private LocalDate missedAppointmentDate;

        @Schema(description = "Days since the appointment. Zero or negative for due_soon rows",
                example = "31")
        private int daysOverdue;

        @Schema(example = "overdue")
        private FollowUpStatus followUpStatus;

        @Schema(description = "Null when the patient has no number on file", example = "+260970000471")
        private String phoneNumber;

        @Schema(description = "When this patient was last contacted, or null", example = "2026-09-29T09:14:00Z")
        private Instant lastContactAttempt;
    }
}
