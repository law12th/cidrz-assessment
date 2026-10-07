package cidrz.assessment.backend.followup.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One recorded attempt to bring a patient back: who recorded it, when the contact happened, and an
 * optional note. Written by {@code POST /api/follow-up/{id}/contacted}.
 *
 * <p>{@code idempotencyKey} is unique. That constraint is what makes a replayed request safe, and it
 * is deliberately in the database rather than in the application: it holds across instances, across
 * restarts, and whether or not Redis is up.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("contact_attempt")
public class ContactAttempt {

    @Id
    @Column("id")
    private UUID id;

    @Column("patient_id")
    private UUID patientId;

    @Column("contacted_at")
    private OffsetDateTime contactedAt;

    @Column("note")
    private String note;

    @Column("recorded_by")
    private String recordedBy;

    @Column("idempotency_key")
    private String idempotencyKey;

    @Column("created_at")
    private OffsetDateTime createdAt;

    @Column("created_by")
    private UUID createdBy;

    @Column("updated_at")
    private OffsetDateTime updatedAt;

    @Column("updated_by")
    private UUID updatedBy;

    @Version
    @Column("version")
    private Integer version;

    @Column("is_deleted")
    private Boolean isDeleted;

    @Column("deleted_at")
    private OffsetDateTime deletedAt;
}
