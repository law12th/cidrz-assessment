package cidrz.assessment.backend.followup;

import cidrz.assessment.backend.common.PagedResponse;
import cidrz.assessment.backend.followup.dto.FollowUpDTO;
import cidrz.assessment.backend.followup.entity.type.FollowUpStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import reactor.core.publisher.Flux;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end against a real PostgreSQL and a real Redis (Testcontainers), with the migration applied
 * from empty — so the migration itself is covered, not just the code that reads what it creates.
 *
 * <p>The clock is fixed to 6 October 2026, the date of the brief's worked example, so the expected
 * list is the one the brief states rather than one that drifts with the calendar.
 *
 * <p>All patient data here is invented.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient(timeout = "30s")
@Import(FollowUpApiIntegrationTest.FixedClock.class)
@ActiveProfiles("test")
@Testcontainers
@ExtendWith(OutputCaptureExtension.class)
class FollowUpApiIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void containers(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://%s:%d/%s".formatted(
                POSTGRES.getHost(), POSTGRES.getMappedPort(5432), POSTGRES.getDatabaseName()));
        registry.add("spring.r2dbc.username", POSTGRES::getUsername);
        registry.add("spring.r2dbc.password", POSTGRES::getPassword);
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock testClock() {
            // 16:00 in Lusaka on 6 October 2026, the date of the brief's worked example.
            // Deliberately late in the working day: the contact attempts these tests record are
            // timestamped 09:14Z (11:14 local), and the service rejects a contacted_at in the
            // future, so an earlier fixed clock would reject the fixture rather than the bug.
            return Clock.fixed(Instant.parse("2026-10-06T14:00:00Z"), ZoneId.of("Africa/Lusaka"));
        }
    }

    private static final ParameterizedTypeReference<PagedResponse<FollowUpDTO.FollowUpItemResponse>> PAGE =
            new ParameterizedTypeReference<>() {
            };

    static final String CLINICIAN_0101 = "test-clinician-0101";
    static final String CLINICIAN_0207 = "test-clinician-0207";
    static final String DISTRICT_MWANSA = "test-district-mwansa";
    static final String RATE_LIMITED = "test-ratelimit-0101";

    static final String THANDIWE = "00000000-0000-0000-0000-000000000471";
    static final String MULENGA = "00000000-0000-0000-0000-000000000472";
    static final String CHIPO = "00000000-0000-0000-0000-000000000018";
    static final String EXACTLY_SEVEN = "00000000-0000-0000-0000-000000000007";
    static final String EIGHT_DAYS = "00000000-0000-0000-0000-000000000008";
    static final String CAME_BACK = "00000000-0000-0000-0000-000000000101";
    static final String SAME_DAY_TIE = "00000000-0000-0000-0000-000000000102";
    static final String DUE_SOON = "00000000-0000-0000-0000-000000000103";
    static final String OTHER_FACILITY = "00000000-0000-0000-0000-000000000104";
    static final String ARCHIVED = "00000000-0000-0000-0000-000000000105";

    @Autowired
    WebTestClient web;

    @Autowired
    DatabaseClient db;

    @Autowired
    ReactiveStringRedisTemplate redis;

    @BeforeEach
    void loadTestData() {
        List<String> statements = List.of(
                "TRUNCATE contact_attempt, visit, patient, facility CASCADE",
                """
                INSERT INTO facility (id, name, district) VALUES
                  ('FAC-0101', 'Mwansa Urban Clinic', 'Mwansa'),
                  ('FAC-0102', 'Mwansa East Clinic', 'Mwansa'),
                  ('FAC-0207', 'Kalemba Rural Health Centre', 'Kalemba')
                """,
                """
                INSERT INTO patient (id, facility_id, patient_number, first_name, last_name,
                                     date_of_birth, sex, phone_number, is_deleted, deleted_at) VALUES
                  ('%s', 'FAC-0101', '0101-000471', 'Thandiwe', 'Banda', '1991-03-14', 'female', '+260970000471', false, NULL),
                  ('%s', 'FAC-0101', '0101-000472', 'Mulenga',  'Phiri', '1978-11-02', 'male',   NULL,            false, NULL),
                  ('%s', 'FAC-0207', '0207-000018', 'Chipo',    'Zulu',  '2015-07-23', 'female', '+260960000018', false, NULL),
                  ('%s', 'FAC-0101', '0101-900007', 'Exactly',  'Seven', '1990-01-01', 'other',  NULL,            false, NULL),
                  ('%s', 'FAC-0101', '0101-900008', 'Eight',    'Days',  '1990-01-01', 'female', NULL,            false, NULL),
                  ('%s', 'FAC-0101', '0101-900101', 'Came',     'Back',  '1990-01-01', 'male',   NULL,            false, NULL),
                  ('%s', 'FAC-0101', '0101-900102', 'Same',     'Day',   '1990-01-01', 'female', NULL,            false, NULL),
                  ('%s', 'FAC-0101', '0101-900103', 'Due',      'Soon',  '1990-01-01', 'male',   NULL,            false, NULL),
                  ('%s', 'FAC-0102', '0102-900104', 'Other',    'Clinic','1990-01-01', 'female', NULL,            false, NULL),
                  ('%s', 'FAC-0101', '0101-900105', 'Archived', 'Record','1990-01-01', 'other',  NULL,            true,  now())
                """.formatted(THANDIWE, MULENGA, CHIPO, EXACTLY_SEVEN, EIGHT_DAYS, CAME_BACK, SAME_DAY_TIE,
                        DUE_SOON, OTHER_FACILITY, ARCHIVED),
                """
                INSERT INTO visit (patient_id, visit_date, next_appointment_date, visit_type, created_at) VALUES
                  -- the brief's starter visits
                  ('%1$s', '2026-07-02', '2026-08-02', 'routine',     '2026-07-02T09:00:00Z'),
                  ('%1$s', '2026-08-05', '2026-09-05', 'follow_up',   '2026-08-05T09:00:00Z'),
                  ('%2$s', '2026-08-20', '2026-09-10', 'routine',     '2026-08-20T09:00:00Z'),
                  ('%3$s', '2026-09-28', NULL,         'unscheduled', '2026-09-28T09:00:00Z'),
                  -- appointment exactly 7 days ago: not "more than" 7
                  ('%4$s', '2026-09-01', '2026-09-29', 'routine',     '2026-09-01T09:00:00Z'),
                  -- appointment 8 days ago: first day on the list
                  ('%5$s', '2026-09-01', '2026-09-28', 'routine',     '2026-09-01T09:00:00Z'),
                  -- missed 1 September, but came back unscheduled on 3 September: off the list
                  ('%6$s', '2026-08-01', '2026-09-01', 'routine',     '2026-08-01T09:00:00Z'),
                  ('%6$s', '2026-09-03', NULL,         'unscheduled', '2026-09-03T09:00:00Z'),
                  -- two visits on one day: the later-created one is the most recent, and set no appointment
                  ('%7$s', '2026-08-01', '2026-09-01', 'routine',     '2026-08-01T09:00:00Z'),
                  ('%7$s', '2026-08-01', NULL,         'unscheduled', '2026-08-01T10:00:00Z'),
                  -- appointment in 3 days
                  ('%8$s', '2026-09-09', '2026-10-09', 'routine',     '2026-09-09T09:00:00Z'),
                  -- overdue, but at another facility in the same district
                  ('%9$s', '2026-08-01', '2026-09-01', 'routine',     '2026-08-01T09:00:00Z'),
                  -- overdue, but the patient record is soft-deleted
                  ('%10$s', '2026-08-01', '2026-09-01', 'routine',    '2026-08-01T09:00:00Z')
                """.formatted(THANDIWE, MULENGA, CHIPO, EXACTLY_SEVEN, EIGHT_DAYS, CAME_BACK, SAME_DAY_TIE,
                        DUE_SOON, OTHER_FACILITY, ARCHIVED));

        Flux.fromIterable(statements).concatMap(sql -> db.sql(sql).then()).blockLast();
    }

    private WebTestClient.ResponseSpec get(String token, String query) {
        WebTestClient.RequestHeadersSpec<?> request = web.get().uri("/api/follow-up?" + query);
        if (token != null) {
            request = request.header("Authorization", "Bearer " + token);
        }
        return request.exchange();
    }

    private PagedResponse<FollowUpDTO.FollowUpItemResponse> list(String token, String query) {
        return get(token, query).expectStatus().isOk()
                .expectBody(PAGE).returnResult().getResponseBody();
    }

    private static List<String> ids(PagedResponse<FollowUpDTO.FollowUpItemResponse> page) {
        return page.getItems().stream().map(FollowUpDTO.FollowUpItemResponse::getId).toList();
    }

    @Nested
    class TheRule {

        @Test
        void matchesTheBriefsWorkedExample() {
            PagedResponse<FollowUpDTO.FollowUpItemResponse> page = list(CLINICIAN_0101, "facility_id=FAC-0101");

            assertThat(ids(page)).containsExactly(THANDIWE, MULENGA, EIGHT_DAYS);
            assertThat(page.getTotal()).isEqualTo(3);

            FollowUpDTO.FollowUpItemResponse thandiwe = page.getItems().getFirst();
            assertThat(thandiwe.getMissedAppointmentDate()).hasToString("2026-09-05");
            assertThat(thandiwe.getDaysOverdue()).isEqualTo(31);
            assertThat(thandiwe.getFollowUpStatus()).isEqualTo(FollowUpStatus.OVERDUE);
            assertThat(thandiwe.getFacilityName()).isEqualTo("Mwansa Urban Clinic");
            assertThat(page.getItems().get(1).getDaysOverdue()).isEqualTo(26);
            assertThat(page.getItems().get(2).getFollowUpStatus()).isEqualTo(FollowUpStatus.MISSED);
        }

        @Test
        void excludesAPatientWhoseLatestVisitSetNoAppointment() {
            PagedResponse<FollowUpDTO.FollowUpItemResponse> page = list(CLINICIAN_0207, "facility_id=FAC-0207");
            assertThat(page.getItems()).isEmpty(); // Chipo Zulu
            assertThat(page.getTotal()).isZero();
        }

        @Test
        void exactlyNDaysIsNotListedButNPlusOneIs() {
            assertThat(ids(list(CLINICIAN_0101, "facility_id=FAC-0101")))
                    .contains(EIGHT_DAYS).doesNotContain(EXACTLY_SEVEN);
            assertThat(ids(list(CLINICIAN_0101, "facility_id=FAC-0101&overdue_days=6")))
                    .contains(EXACTLY_SEVEN);
        }

        @Test
        void aLaterVisitClearsTheMissedAppointmentIncludingASameDayTieBrokenByCreationTime() {
            assertThat(ids(list(CLINICIAN_0101, "facility_id=FAC-0101&overdue_days=0")))
                    .doesNotContain(CAME_BACK, SAME_DAY_TIE);
        }

        @Test
        void thresholdIsAppliedInTheDatabase() {
            assertThat(ids(list(CLINICIAN_0101, "facility_id=FAC-0101&overdue_days=30")))
                    .containsExactly(THANDIWE);
            assertThat(list(CLINICIAN_0101, "facility_id=FAC-0101&overdue_days=31").getItems()).isEmpty();
        }

        @Test
        void filtersByStatus() {
            assertThat(ids(list(CLINICIAN_0101, "facility_id=FAC-0101&status=overdue")))
                    .containsExactly(THANDIWE, MULENGA);
            assertThat(ids(list(CLINICIAN_0101, "facility_id=FAC-0101&status=missed")))
                    .containsExactly(EIGHT_DAYS);

            PagedResponse<FollowUpDTO.FollowUpItemResponse> dueSoon =
                    list(CLINICIAN_0101, "facility_id=FAC-0101&status=due_soon");
            assertThat(ids(dueSoon)).containsExactly(DUE_SOON);
            assertThat(dueSoon.getItems().getFirst().getDaysOverdue()).isEqualTo(-3);
            assertThat(dueSoon.getItems().getFirst().getFollowUpStatus()).isEqualTo(FollowUpStatus.DUE_SOON);
        }

        @Test
        void neverReturnsAnotherFacilitysPatients() {
            assertThat(ids(list(DISTRICT_MWANSA, "facility_id=FAC-0101"))).doesNotContain(OTHER_FACILITY);
            assertThat(ids(list(DISTRICT_MWANSA, "facility_id=FAC-0102"))).containsExactly(OTHER_FACILITY);
        }

        /**
         * Pins the wire shape, not just the round trip. Deserialising into our own DTO would pass
         * even if every field were named wrongly, because both ends apply the same naming strategy;
         * the front end in web/ reads these exact keys (appendix 15.3).
         *
         * <p>Compared in STRICT mode deliberately. A lenient comparison, and jsonPath's
         * doesNotExist, both treat a field that is present and null as indistinguishable from a
         * field that is absent — and the front end does not: its types declare
         * {@code last_contact_attempt: string | null}, so an omitted key is a different value.
         */
        @Test
        void serialisesTheContractsFieldNamesExactly() {
            web.get().uri("/api/follow-up?facility_id=FAC-0101&page=1&page_size=1")
                    .header("Authorization", "Bearer " + CLINICIAN_0101)
                    .exchange().expectStatus().isOk()
                    .expectBody()
                    .json("""
                            {
                              "page": 1,
                              "page_size": 1,
                              "total": 3,
                              "items": [{
                                "id": "%s",
                                "patient_number": "0101-000471",
                                "first_name": "Thandiwe",
                                "last_name": "Banda",
                                "facility_id": "FAC-0101",
                                "facility_name": "Mwansa Urban Clinic",
                                "missed_appointment_date": "2026-09-05",
                                "days_overdue": 31,
                                "follow_up_status": "overdue",
                                "phone_number": "+260970000471",
                                "last_contact_attempt": null
                              }]
                            }
                            """.formatted(THANDIWE), JsonCompareMode.STRICT);
        }

        @Test
        void neverReturnsASoftDeletedPatient() {
            assertThat(ids(list(CLINICIAN_0101, "facility_id=FAC-0101&overdue_days=0")))
                    .doesNotContain(ARCHIVED);
        }
    }

    @Nested
    class SortingAndPaging {

        @Test
        void sortsAscending() {
            assertThat(ids(list(CLINICIAN_0101, "facility_id=FAC-0101&sort=days_overdue_asc")))
                    .containsExactly(EIGHT_DAYS, MULENGA, THANDIWE);
        }

        @Test
        void pagesWithAStableTotal() {
            PagedResponse<FollowUpDTO.FollowUpItemResponse> second =
                    list(CLINICIAN_0101, "facility_id=FAC-0101&page=2&page_size=1");
            assertThat(ids(second)).containsExactly(MULENGA);
            assertThat(second.getTotal()).isEqualTo(3);
            assertThat(second.getPage()).isEqualTo(2);
            assertThat(second.getPageSize()).isEqualTo(1);
        }

        @Test
        void aPagePastTheEndIsEmptyButStillReportsTheTotal() {
            PagedResponse<FollowUpDTO.FollowUpItemResponse> past =
                    list(CLINICIAN_0101, "facility_id=FAC-0101&page=9&page_size=2");
            assertThat(past.getItems()).isEmpty();
            assertThat(past.getTotal()).isEqualTo(3);
        }
    }

    @Nested
    class Validation {

        @Test
        void returnsTheContractErrorShapeWithTheCorrelationId() {
            get(CLINICIAN_0101, "status=overdue")
                    .expectStatus().isBadRequest()
                    .expectHeader().valueEquals("X-Correlation-Id", "test-correlation-0001")
                    .expectBody()
                    .jsonPath("$.error.code").isEqualTo("VALIDATION_FAILED")
                    .jsonPath("$.error.message").isEqualTo("facility_id is required")
                    .jsonPath("$.error.correlation_id").isEqualTo("test-correlation-0001");
        }

        @Test
        void rejectsBadParameters() {
            for (String query : List.of(
                    "facility_id=FAC-0101&status=late",
                    "facility_id=FAC-0101&page_size=500",
                    "facility_id=FAC-0101&overdue_days=abc",
                    "facility=FAC-0101")) {
                get(CLINICIAN_0101, query).expectStatus().isBadRequest()
                        .expectBody().jsonPath("$.error.code").isEqualTo("VALIDATION_FAILED");
            }
        }

        @Test
        void unknownRoutesUseTheSameErrorShape() {
            web.get().uri("/api/nothing-here").header("Authorization", "Bearer " + CLINICIAN_0101)
                    .exchange().expectStatus().isNotFound()
                    .expectBody().jsonPath("$.error.code").isEqualTo("NOT_FOUND")
                    .jsonPath("$.error.correlation_id").isNotEmpty();
        }

        // Overrides get() for this class, so the first test can supply its own correlation ID.
        private WebTestClient.ResponseSpec get(String token, String query) {
            return web.get().uri("/api/follow-up?" + query)
                    .header("Authorization", "Bearer " + token)
                    .header("X-Correlation-Id", "test-correlation-0001")
                    .exchange();
        }
    }

    @Nested
    class Access {

        @Test
        void requiresAToken() {
            get(null, "facility_id=FAC-0101").expectStatus().isUnauthorized()
                    .expectBody().jsonPath("$.error.code").isEqualTo("UNAUTHENTICATED");
            get("not-a-real-token", "facility_id=FAC-0101").expectStatus().isUnauthorized();
        }

        @Test
        void clinicianReadsOnlyTheirOwnFacility() {
            get(CLINICIAN_0101, "facility_id=FAC-0207").expectStatus().isForbidden()
                    .expectBody().jsonPath("$.error.code").isEqualTo("FORBIDDEN");
        }

        @Test
        void districtOfficerReadsEveryFacilityInTheirDistrictOnly() {
            get(DISTRICT_MWANSA, "facility_id=FAC-0101").expectStatus().isOk();
            get(DISTRICT_MWANSA, "facility_id=FAC-0102").expectStatus().isOk();
            get(DISTRICT_MWANSA, "facility_id=FAC-0207").expectStatus().isForbidden();
            get(DISTRICT_MWANSA, "facility_id=FAC-9999").expectStatus().isNotFound();
        }
    }

    @Nested
    class RecordingContact {

        private WebTestClient.ResponseSpec post(String token, String patientId, Object body, String key) {
            WebTestClient.RequestBodySpec request = web.post()
                    .uri("/api/follow-up/" + patientId + "/contacted")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON);
            if (key != null) {
                request = request.header("Idempotency-Key", key);
            }
            return request.bodyValue(body).exchange();
        }

        private long attemptsFor(String patientId) {
            return db.sql("SELECT count(*) AS n FROM contact_attempt WHERE patient_id = CAST(:id AS uuid)")
                    .bind("id", patientId)
                    .map(row -> row.get("n", Long.class)).one().block();
        }

        @Test
        void clinicianRecordsAContactAndTheListShowsIt() {
            post(CLINICIAN_0101, THANDIWE,
                    Map.of("contacted_at", "2026-10-06T09:14:00Z", "note", "Phoned, will come Friday"), null)
                    .expectStatus().isNoContent();

            FollowUpDTO.FollowUpItemResponse thandiwe =
                    list(CLINICIAN_0101, "facility_id=FAC-0101").getItems().getFirst();
            assertThat(thandiwe.getLastContactAttempt()).isEqualTo(Instant.parse("2026-10-06T09:14:00Z"));
        }

        @Test
        void replayingTheSameIdempotencyKeyRecordsTheAttemptOnce() {
            Map<String, String> body = Map.of("contacted_at", "2026-10-06T09:14:00Z");
            post(CLINICIAN_0101, MULENGA, body, "replay-key-0001").expectStatus().isNoContent();
            post(CLINICIAN_0101, MULENGA, body, "replay-key-0001").expectStatus().isNoContent();
            assertThat(attemptsFor(MULENGA)).isEqualTo(1);
        }

        @Test
        void districtOfficerCannotWriteWhateverTheirBodyContains() {
            post(DISTRICT_MWANSA, THANDIWE, Map.of("contacted_at", "2026-10-06T09:14:00Z"), null)
                    .expectStatus().isForbidden();
            post(DISTRICT_MWANSA, THANDIWE, Map.of("nonsense", true), null)
                    .expectStatus().isForbidden();
            assertThat(attemptsFor(THANDIWE)).isZero();
        }

        @Test
        void aPatientAtAnotherFacilityLooksLikeNoPatientAtAll() {
            post(CLINICIAN_0207, THANDIWE, Map.of("contacted_at", "2026-10-06T09:14:00Z"), null)
                    .expectStatus().isNotFound();
            post(CLINICIAN_0207, "not-a-uuid", Map.of("contacted_at", "2026-10-06T09:14:00Z"), null)
                    .expectStatus().isNotFound();
        }

        @Test
        void validatesTheBody() {
            post(CLINICIAN_0101, THANDIWE, Map.of("note", "no timestamp"), null)
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.error.message").isEqualTo("contacted_at is required");
            post(CLINICIAN_0101, THANDIWE, Map.of("contacted_at", "yesterday"), null)
                    .expectStatus().isBadRequest();
            post(CLINICIAN_0101, THANDIWE, Map.of("contacted_at", "2026-10-07T08:00:00Z"), null)
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.error.message").isEqualTo("contacted_at cannot be in the future");
            post(CLINICIAN_0101, THANDIWE,
                    Map.of("contacted_at", "2026-10-06T09:14:00Z", "note", "x".repeat(501)), null)
                    .expectStatus().isBadRequest();
            assertThat(attemptsFor(THANDIWE)).isZero();
        }

        @Test
        void rejectsAMalformedIdempotencyKeyRatherThanIgnoringIt() {
            post(CLINICIAN_0101, THANDIWE, Map.of("contacted_at", "2026-10-06T09:14:00Z"), "short")
                    .expectStatus().isBadRequest()
                    .expectBody().jsonPath("$.error.code").isEqualTo("VALIDATION_FAILED");
            assertThat(attemptsFor(THANDIWE)).isZero();
        }
    }

    @Nested
    class RateLimiting {

        /**
         * Uses its own principal, and clears its own window first, so the limit it trips is the one
         * this test caused and not one the rest of the suite contributed to.
         */
        @Test
        void refusesAFloodOfRequestsWithTheContractErrorShape() {
            String window = "carelink:rl:follow-up:list:ratelimit.fac0101";
            redis.delete(window).block();

            int limit = 120;
            for (int i = 0; i < limit; i++) {
                get(RATE_LIMITED, "facility_id=FAC-0101&page_size=1").expectStatus().isOk();
            }

            get(RATE_LIMITED, "facility_id=FAC-0101&page_size=1")
                    .expectStatus().isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
                    .expectBody().jsonPath("$.error.code").isEqualTo("RATE_LIMIT_EXCEEDED");
        }
    }

    @Nested
    class Logging {

        @Test
        void logsCarryTheCorrelationIdAndNoPatientDetails(CapturedOutput output) {
            web.get().uri("/api/follow-up?facility_id=FAC-0101")
                    .header("Authorization", "Bearer " + CLINICIAN_0101)
                    .header("X-Correlation-Id", "log-check-00000001")
                    .exchange().expectStatus().isOk();
            web.post().uri("/api/follow-up/" + THANDIWE + "/contacted")
                    .header("Authorization", "Bearer " + CLINICIAN_0101)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("contacted_at", "2026-10-06T09:14:00Z", "note", "secret-note-text"))
                    .exchange().expectStatus().isNoContent();

            String logs = output.getAll();
            assertThat(logs.lines().filter(line -> line.contains("follow-up list served")))
                    .isNotEmpty()
                    .allMatch(line -> line.contains("log-check-00000001"));
            assertThat(logs).doesNotContain("Thandiwe", "Banda", "+260970000471", "0101-000471",
                    "secret-note-text", CLINICIAN_0101);
        }
    }
}
