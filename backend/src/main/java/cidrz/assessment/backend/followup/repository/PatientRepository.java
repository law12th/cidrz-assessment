package cidrz.assessment.backend.followup.repository;

import cidrz.assessment.backend.followup.entity.Patient;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public interface PatientRepository extends R2dbcRepository<Patient, UUID> {

    /**
     * The facility a patient belongs to, or empty if there is no such live patient.
     *
     * <p>Only the one column is read. Loading a whole patient to compare a facility id would pull
     * names, a date of birth and a phone number into memory for an authorisation check that needs
     * none of them, and every field loaded is a field that can end up in a log line.
     */
    @Query("SELECT facility_id FROM patient WHERE id = :id AND is_deleted = false")
    Mono<String> findFacilityIdById(UUID id);
}
