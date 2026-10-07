package cidrz.assessment.backend.followup.repository;

import cidrz.assessment.backend.followup.entity.type.SortOrder;
import cidrz.assessment.backend.followup.service.impl.FollowUpRules;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * All filtering, sorting, paging and counting happen in PostgreSQL, as the brief requires. Nothing
 * here loads a facility's patients and sifts them in memory — at 35,000 patients for one facility
 * that is the difference between a page and an outage.
 *
 * <p>For each patient at the facility a LATERAL subquery takes the single most recent visit
 * ({@code visit_date}, then {@code created_at}, then {@code id} as tie-breaks) straight from
 * {@code visit_latest_idx}, without touching the visit table. Only that visit's
 * {@code next_appointment_date} counts, so any later visit — attended, unscheduled, whatever —
 * automatically takes the patient off the list, which is the behaviour a clinic expects.
 *
 * <p>Measured on the seeded data (100,000 patients, 400,000 visits) for the largest facility
 * (35,002 patients, 15,566 listed): about 180 ms for any page. See the README.
 *
 * <p>This is the one repository written against {@link DatabaseClient} rather than as an
 * {@code R2dbcRepository} interface. A derived query cannot express LATERAL, a window count and a
 * correlated subquery, and generating this SQL from method names would hide the part of the module
 * most worth reading.
 */
@Repository
@RequiredArgsConstructor
public class FollowUpRepository {

    private static final String FROM_WHERE = """
            FROM patient p
            JOIN facility f ON f.id = p.facility_id AND f.is_deleted = false
            CROSS JOIN LATERAL (
                SELECT v.next_appointment_date
                FROM visit v
                WHERE v.patient_id = p.id
                  AND v.is_deleted = false
                ORDER BY v.visit_date DESC, v.created_at DESC, v.id DESC
                LIMIT 1
            ) latest
            WHERE p.facility_id = :facilityId
              AND p.is_deleted = false
              AND latest.next_appointment_date IS NOT NULL
              AND (CAST(:today AS date) - latest.next_appointment_date) BETWEEN :minDays AND :maxDays
            """;

    private static final String SELECT_PAGE = """
            SELECT p.id, p.patient_number, p.first_name, p.last_name, p.facility_id,
                   f.name AS facility_name,
                   latest.next_appointment_date AS appointment_date,
                   (CAST(:today AS date) - latest.next_appointment_date) AS days_overdue,
                   p.phone_number,
                   (SELECT max(c.contacted_at) FROM contact_attempt c
                     WHERE c.patient_id = p.id AND c.is_deleted = false) AS last_contact_attempt,
                   count(*) OVER () AS total
            """ + FROM_WHERE;

    // The sort direction cannot be a bind parameter, so each direction is its own fixed statement
    // rather than string-built SQL. p.id breaks ties, so paging stays stable when many patients
    // share a days_overdue value — without it, a row can appear on two pages or on none.
    private static final String PAGE_DESC = SELECT_PAGE
            + " ORDER BY days_overdue DESC, p.id LIMIT :limit OFFSET :offset";
    private static final String PAGE_ASC = SELECT_PAGE
            + " ORDER BY days_overdue ASC, p.id LIMIT :limit OFFSET :offset";
    private static final String COUNT = "SELECT count(*) AS total " + FROM_WHERE;

    private final DatabaseClient databaseClient;

    /** One row of the follow-up query, before the rule classifies it. */
    public record FollowUpRow(
            UUID id,
            String patientNumber,
            String firstName,
            String lastName,
            String facilityId,
            String facilityName,
            LocalDate appointmentDate,
            int daysOverdue,
            String phoneNumber,
            Instant lastContactAttempt,
            long total) {
    }

    public record PageResult(List<FollowUpRow> rows, long total) {
    }

    public Mono<PageResult> findPage(String facilityId, LocalDate today, FollowUpRules.DayRange range,
                                     SortOrder sort, int limit, long offset) {

        String sql = sort == SortOrder.DAYS_OVERDUE_ASC ? PAGE_ASC : PAGE_DESC;
        return databaseClient.sql(sql)
                .bind("facilityId", facilityId)
                .bind("today", today)
                .bind("minDays", range.minDays())
                .bind("maxDays", range.maxDays())
                .bind("limit", limit)
                .bind("offset", offset)
                .map(FollowUpRepository::toRow)
                .all()
                .collectList()
                .flatMap(rows -> {
                    if (!rows.isEmpty()) {
                        // The window count rides along on every row, so the total costs no extra query.
                        return Mono.just(new PageResult(rows, rows.getFirst().total()));
                    }
                    if (offset == 0) {
                        return Mono.just(new PageResult(List.of(), 0));
                    }
                    // A page past the end has no row to carry the window count, so count separately.
                    return count(facilityId, today, range).map(total -> new PageResult(List.of(), total));
                });
    }

    private Mono<Long> count(String facilityId, LocalDate today, FollowUpRules.DayRange range) {
        return databaseClient.sql(COUNT)
                .bind("facilityId", facilityId)
                .bind("today", today)
                .bind("minDays", range.minDays())
                .bind("maxDays", range.maxDays())
                .map(row -> row.get("total", Long.class))
                .one();
    }

    private static FollowUpRow toRow(Readable row) {
        OffsetDateTime lastContact = row.get("last_contact_attempt", OffsetDateTime.class);
        return new FollowUpRow(
                row.get("id", UUID.class),
                row.get("patient_number", String.class),
                row.get("first_name", String.class),
                row.get("last_name", String.class),
                row.get("facility_id", String.class),
                row.get("facility_name", String.class),
                row.get("appointment_date", LocalDate.class),
                row.get("days_overdue", Integer.class),
                row.get("phone_number", String.class),
                lastContact == null ? null : lastContact.toInstant(),
                row.get("total", Long.class));
    }
}
