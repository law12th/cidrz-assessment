package cidrz.assessment.backend.followup.repository;

import cidrz.assessment.backend.followup.entity.ContactAttempt;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.UUID;

@Repository
public interface ContactAttemptRepository extends R2dbcRepository<ContactAttempt, UUID> {

    /**
     * Records a contact attempt, ignoring a repeated Idempotency-Key.
     *
     * <p>Written as explicit SQL rather than {@code save()} because {@code save()} cannot express
     * {@code ON CONFLICT DO NOTHING}, and that clause is the whole point: a client replaying a
     * queued request after a timeout — the Section D1 scenario — must not record the attempt twice.
     * The unique constraint absorbs the replay inside the database, so it holds across instances
     * without a lock and without a read-then-write race.
     *
     * @return 1 if a new row was written, 0 if this key had already been recorded
     */
    @Modifying
    @Query("""
            INSERT INTO contact_attempt (patient_id, contacted_at, note, recorded_by, idempotency_key)
            VALUES (:patientId, :contactedAt, :note, :recordedBy, :idempotencyKey)
            ON CONFLICT (idempotency_key) DO NOTHING
            """)
    Mono<Long> insertIgnoringReplay(UUID patientId,
                                    OffsetDateTime contactedAt,
                                    String note,
                                    String recordedBy,
                                    String idempotencyKey);
}
