package cidrz.assessment.backend.facility.repository;

import cidrz.assessment.backend.facility.entity.Facility;
import org.springframework.data.r2dbc.repository.R2dbcRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface FacilityRepository extends R2dbcRepository<Facility, String> {

    /**
     * Always prefer this to {@code findById}: a closed facility is soft-deleted, and its patients
     * must stop appearing on anyone's list without its historical records being destroyed.
     */
    Mono<Facility> findByIdAndIsDeletedFalse(String id);
}
