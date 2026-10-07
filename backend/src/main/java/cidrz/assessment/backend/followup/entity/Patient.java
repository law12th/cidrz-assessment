package cidrz.assessment.backend.followup.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A patient, as the follow-up module needs them. The full clinical record lives elsewhere in
 * CareLink; this module reads identity, contact details and the facility a patient belongs to.
 *
 * <p>Soft-deleted rather than deleted, like everything else here. These are clinical records: the
 * brief's Section D1 is explicit that duplicate patients created by a replayed sync are "not rows
 * to be deleted casually". Merging a duplicate marks the loser deleted and keeps it readable.
 *
 * <p>There is no {@code Visit} entity. Visits are read only through the follow-up projection in
 * {@code FollowUpRepository}, which never materialises a whole visit row, so an entity for them
 * would be code nothing calls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("patient")
public class Patient {

    @Id
    @Column("id")
    private UUID id;

    @Column("facility_id")
    private String facilityId;

    @Column("patient_number")
    private String patientNumber;

    @Column("first_name")
    private String firstName;

    @Column("last_name")
    private String lastName;

    @Column("date_of_birth")
    private LocalDate dateOfBirth;

    @Column("sex")
    private String sex;

    @Column("phone_number")
    private String phoneNumber;

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
