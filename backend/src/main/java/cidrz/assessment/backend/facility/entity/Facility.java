package cidrz.assessment.backend.facility.entity;

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
 * A health facility. The id is the national facility code ('FAC-0101'), not a surrogate key: it is
 * already unique, it is stable across the laboratory system and the reporting platform, and it is
 * what clinic staff read off a form. Joining on a code the other systems also use is what lets the
 * two tiers reconcile without a mapping table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("facility")
public class Facility {

    @Id
    @Column("id")
    private String id;

    @Column("name")
    private String name;

    @Column("district")
    private String district;

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
