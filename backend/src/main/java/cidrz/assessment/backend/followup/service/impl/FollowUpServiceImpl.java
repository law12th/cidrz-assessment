package cidrz.assessment.backend.followup.service.impl;

import cidrz.assessment.backend.auth.AccessPolicy;
import cidrz.assessment.backend.auth.CareLinkPrincipal;
import cidrz.assessment.backend.common.PagedResponse;
import cidrz.assessment.backend.config.ConfigProperties;
import cidrz.assessment.backend.exception.ResourceNotFoundException;
import cidrz.assessment.backend.exception.ValidationException;
import cidrz.assessment.backend.facility.entity.Facility;
import cidrz.assessment.backend.followup.dto.FollowUpDTO;
import cidrz.assessment.backend.followup.repository.ContactAttemptRepository;
import cidrz.assessment.backend.followup.repository.FollowUpRepository;
import cidrz.assessment.backend.followup.repository.PatientRepository;
import cidrz.assessment.backend.followup.service.FollowUpService;
import cidrz.assessment.backend.redis.idempotency.ContactAttemptIdempotencyCache;
import cidrz.assessment.backend.redis.ratelimit.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The follow-up rules, in one place.
 *
 * <p>Logs here carry identifiers and counts only — never a name, a phone number, a patient number
 * or the text of a note. Logs leave the facility, are read by people who are not treating the
 * patient, and outlive the request; treating them as a place personal health information must never
 * reach is cheaper than auditing them later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowUpServiceImpl implements FollowUpService {

    private static final int MAX_NOTE_LENGTH = 500;
    private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofMinutes(5);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_-]{8,100}$");

    private final FollowUpRepository followUpRepository;
    private final PatientRepository patientRepository;
    private final ContactAttemptRepository contactAttemptRepository;
    private final ContactAttemptIdempotencyCache idempotencyCache;
    private final RateLimiterService rateLimiterService;
    private final FollowUpQueryParser queryParser;
    private final AccessPolicy accessPolicy;
    private final FollowUpRules rules;
    private final ConfigProperties configProperties;
    private final Clock clock;

    @Override
    public Mono<PagedResponse<FollowUpDTO.FollowUpItemResponse>> list(
            CareLinkPrincipal principal, MultiValueMap<String, String> params) {

        return Mono.defer(() -> {
            FollowUpDTO.FollowUpQuery query = queryParser.parse(params);
            LocalDate today = LocalDate.now(clock);
            FollowUpRules.DayRange range = rules.rangeFor(query.getStatus(), query.getOverdueDays());

            return rateLimit(principal, "follow-up:list")
                    .then(accessPolicy.authorizeRead(principal, query.getFacilityId()))
                    .flatMap(facility -> followUpRepository
                            .findPage(facility.getId(), today, range, query.getSort(),
                                    query.getPageSize(), query.offset())
                            .map(result -> toPage(facility, result, query)))
                    .doOnNext(page -> log.atInfo()
                            .addKeyValue("facility_id", query.getFacilityId())
                            .addKeyValue("role", principal.role().name())
                            .addKeyValue("status_filter", query.getStatus() == null
                                    ? "default" : query.getStatus().getWireValue())
                            .addKeyValue("total", page.getTotal())
                            .addKeyValue("returned", page.getItems().size())
                            .log("follow-up list served"));
        });
    }

    @Override
    public Mono<Void> recordContact(CareLinkPrincipal principal, String rawPatientId,
                                    Mono<FollowUpDTO.ContactRequest> body, String idempotencyKey) {

        return Mono.defer(() -> {
                    // Before the body is even read, so a district officer gets 403 whatever they send.
                    accessPolicy.requireWriteRole(principal);
                    return rateLimit(principal, "follow-up:contact").then(body);
                })
                .flatMap(request -> {
                    Instant contactedAt = validContactedAt(request);
                    String note = validNote(request);
                    String key = validIdempotencyKey(idempotencyKey);
                    UUID patientId = parsePatientId(rawPatientId);

                    return alreadyRecorded(key)
                            .flatMap(replayed -> replayed
                                    ? logReplay(patientId)
                                    : writeContact(principal, patientId, contactedAt, note, key));
                })
                .then();
    }

    private Mono<Void> writeContact(CareLinkPrincipal principal, UUID patientId,
                                    Instant contactedAt, String note, String key) {
        return patientRepository.findFacilityIdById(patientId)
                // A patient at another facility gets the same 404 as one that does not exist, so the
                // endpoint cannot be used to discover which patients a neighbouring clinic holds.
                .filter(facilityId -> accessPolicy.canWritePatientAt(principal, facilityId))
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Patient not found")))
                .flatMap(facilityId -> contactAttemptRepository
                        .insertIgnoringReplay(patientId,
                                OffsetDateTime.ofInstant(contactedAt, ZoneOffset.UTC),
                                note, principal.subject(), key)
                        .defaultIfEmpty(0L)
                        .flatMap(inserted -> {
                            log.atInfo()
                                    .addKeyValue("facility_id", facilityId)
                                    .addKeyValue("patient_id", patientId)
                                    .addKeyValue("replayed", inserted == 0)
                                    .log("contact attempt recorded");
                            // Only after the row is durable: marking first would let a failed insert
                            // suppress the client's genuine retry and lose the attempt silently.
                            return key == null
                                    ? Mono.<Void>empty()
                                    : idempotencyCache.markRecorded(key, configProperties.getIdempotencyTtl());
                        }));
    }

    private Mono<Boolean> alreadyRecorded(String key) {
        return key == null ? Mono.just(false) : idempotencyCache.isRecorded(key);
    }

    private Mono<Void> logReplay(UUID patientId) {
        return Mono.fromRunnable(() -> log.atInfo()
                .addKeyValue("patient_id", patientId)
                .addKeyValue("replayed", true)
                .log("contact attempt replayed, answered from cache"));
    }

    /**
     * Keyed on the principal rather than on the IP address: a whole clinic sits behind one NAT, so
     * an IP-keyed limit would throttle a busy facility rather than the one browser looping.
     */
    private Mono<Void> rateLimit(CareLinkPrincipal principal, String endpoint) {
        return rateLimiterService.enforce(
                "carelink:rl:" + endpoint + ":" + principal.subject(),
                configProperties.getRateLimitMaxRequests(),
                configProperties.getRateLimitWindow());
    }

    private PagedResponse<FollowUpDTO.FollowUpItemResponse> toPage(
            Facility facility, FollowUpRepository.PageResult result, FollowUpDTO.FollowUpQuery query) {

        List<FollowUpDTO.FollowUpItemResponse> items = result.rows().stream()
                .map(row -> FollowUpDTO.FollowUpItemResponse.builder()
                        .id(row.id().toString())
                        .patientNumber(row.patientNumber())
                        .firstName(row.firstName())
                        .lastName(row.lastName())
                        .facilityId(row.facilityId())
                        .facilityName(facility.getName())
                        .missedAppointmentDate(row.appointmentDate())
                        .daysOverdue(row.daysOverdue())
                        .followUpStatus(rules.classify(row.daysOverdue(), query.getOverdueDays())
                                // The SQL and the rule disagree, which can only be a bug. Fail loudly
                                // rather than label a patient with a status the rule does not support.
                                .orElseThrow(() -> new IllegalStateException(
                                        "Row outside every status range: days_overdue="
                                                + row.daysOverdue())))
                        .phoneNumber(row.phoneNumber())
                        .lastContactAttempt(row.lastContactAttempt())
                        .build())
                .toList();

        return PagedResponse.<FollowUpDTO.FollowUpItemResponse>builder()
                .page(query.getPage())
                .pageSize(query.getPageSize())
                .total(result.total())
                .items(items)
                .build();
    }

    private Instant validContactedAt(FollowUpDTO.ContactRequest request) {
        if (request == null || request.getContactedAt() == null || request.getContactedAt().isBlank()) {
            throw new ValidationException("contacted_at is required");
        }
        Instant contactedAt;
        try {
            contactedAt = OffsetDateTime.parse(request.getContactedAt()).toInstant();
        } catch (DateTimeParseException e) {
            throw new ValidationException(
                    "contacted_at must be an ISO 8601 timestamp with an offset, e.g. 2026-10-06T09:14:00Z");
        }
        // A small skew is allowed: the clinic computer's clock, not the staff member, is usually wrong.
        if (contactedAt.isAfter(clock.instant().plus(ALLOWED_CLOCK_SKEW))) {
            throw new ValidationException("contacted_at cannot be in the future");
        }
        return contactedAt;
    }

    private static String validNote(FollowUpDTO.ContactRequest request) {
        String note = request.getNote() == null ? null : request.getNote().strip();
        if (note != null && note.length() > MAX_NOTE_LENGTH) {
            throw new ValidationException("note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return note == null || note.isEmpty() ? null : note;
    }

    private static String validIdempotencyKey(String key) {
        if (key == null) {
            return null;
        }
        if (!IDEMPOTENCY_KEY.matcher(key).matches()) {
            throw new ValidationException("Idempotency-Key must be 8 to 100 letters, digits, '-' or '_'");
        }
        return key;
    }

    /**
     * An unparseable id is reported as "not found" rather than as a validation failure: to the
     * caller the two are the same outcome, and a 400 here would distinguish "this id is malformed"
     * from "this id is not yours", which is the distinction we are trying not to publish.
     */
    private static UUID parsePatientId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Patient not found");
        }
    }
}
