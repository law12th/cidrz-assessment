package cidrz.assessment.backend.followup.service.impl;

import cidrz.assessment.backend.exception.ValidationException;
import cidrz.assessment.backend.followup.FollowUpProperties;
import cidrz.assessment.backend.followup.dto.FollowUpDTO;
import cidrz.assessment.backend.followup.entity.type.FollowUpStatus;
import cidrz.assessment.backend.followup.entity.type.SortOrder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns raw query parameters into a validated {@link FollowUpDTO.FollowUpQuery}, or refuses them.
 *
 * <p>Unknown and repeated parameters are rejected rather than ignored. {@code ?facility=FAC-0101}
 * is a plausible typo, and silently ignoring it would mean returning somebody else's facility — or
 * a 400 that says {@code facility_id is required} while the caller is looking at a URL that
 * appears to contain one. Every message names the parameter and what was expected, and none of them
 * quotes the rejected value back.
 */
@Component
@RequiredArgsConstructor
public class FollowUpQueryParser {

    static final int MAX_OVERDUE_DAYS = 365;
    static final int MAX_PAGE = 10_000;

    private static final Pattern FACILITY_ID = Pattern.compile("^FAC-[0-9]{4}$");
    private static final Set<String> KNOWN = Set.of(
            "facility_id", "status", "overdue_days", "sort", "page", "page_size");

    private final FollowUpProperties properties;

    public FollowUpDTO.FollowUpQuery parse(MultiValueMap<String, String> params) {
        for (var entry : params.entrySet()) {
            if (!KNOWN.contains(entry.getKey())) {
                throw new ValidationException("Unknown query parameter: " + entry.getKey());
            }
            if (entry.getValue().size() > 1) {
                throw new ValidationException(entry.getKey() + " must be given only once");
            }
        }

        String facilityId = single(params, "facility_id");
        if (facilityId == null || facilityId.isBlank()) {
            throw new ValidationException("facility_id is required");
        }
        if (!FACILITY_ID.matcher(facilityId).matches()) {
            throw new ValidationException("facility_id must look like FAC-0101");
        }

        String rawStatus = single(params, "status");
        FollowUpStatus status = rawStatus == null ? null : FollowUpStatus.fromWire(rawStatus)
                .orElseThrow(() -> new ValidationException("status must be one of due_soon, overdue, missed"));

        String rawSort = single(params, "sort");
        SortOrder sort = rawSort == null ? SortOrder.DAYS_OVERDUE_DESC : SortOrder.fromWire(rawSort)
                .orElseThrow(() -> new ValidationException("sort must be days_overdue_desc or days_overdue_asc"));

        return FollowUpDTO.FollowUpQuery.builder()
                .facilityId(facilityId)
                .status(status)
                .sort(sort)
                .overdueDays(intParam(params, "overdue_days",
                        properties.defaultOverdueDays(), 0, MAX_OVERDUE_DAYS))
                .page(intParam(params, "page", 1, 1, MAX_PAGE))
                .pageSize(intParam(params, "page_size",
                        properties.defaultPageSize(), 1, properties.maxPageSize()))
                .build();
    }

    private static String single(MultiValueMap<String, String> params, String name) {
        List<String> values = params.get(name);
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    private static int intParam(MultiValueMap<String, String> params, String name,
                                int fallback, int min, int max) {
        String raw = single(params, name);
        if (raw == null) {
            return fallback;
        }
        int value;
        try {
            value = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(name + " must be a whole number");
        }
        if (value < min || value > max) {
            // The bound is part of the contract, so naming it is useful, not a leak.
            throw new ValidationException(name + " must be between " + min + " and " + max);
        }
        return value;
    }
}
