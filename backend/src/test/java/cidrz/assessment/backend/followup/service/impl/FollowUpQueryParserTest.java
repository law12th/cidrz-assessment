package cidrz.assessment.backend.followup.service.impl;

import cidrz.assessment.backend.exception.ValidationException;
import cidrz.assessment.backend.followup.FollowUpProperties;
import cidrz.assessment.backend.followup.dto.FollowUpDTO;
import cidrz.assessment.backend.followup.entity.type.FollowUpStatus;
import cidrz.assessment.backend.followup.entity.type.SortOrder;
import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FollowUpQueryParserTest {

    private static final FollowUpProperties DEFAULTS = new FollowUpProperties(7, 7, 14, 50, 200);

    private final FollowUpQueryParser parser = new FollowUpQueryParser(DEFAULTS);

    private static MultiValueMap<String, String> params(Map<String, String> values) {
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        values.forEach(map::add);
        return map;
    }

    private void assertRejected(MultiValueMap<String, String> params, String message) {
        assertThatThrownBy(() -> parser.parse(params))
                .isInstanceOf(ValidationException.class)
                .hasMessage(message)
                .extracting(e -> ((ValidationException) e).getErrorCode())
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void appliesDefaults() {
        FollowUpDTO.FollowUpQuery query = parser.parse(params(Map.of("facility_id", "FAC-0101")));

        assertThat(query.getFacilityId()).isEqualTo("FAC-0101");
        assertThat(query.getStatus()).isNull();
        assertThat(query.getOverdueDays()).isEqualTo(7);
        assertThat(query.getSort()).isEqualTo(SortOrder.DAYS_OVERDUE_DESC);
        assertThat(query.getPage()).isEqualTo(1);
        assertThat(query.getPageSize()).isEqualTo(50);
        assertThat(query.offset()).isZero();
    }

    @Test
    void parsesEveryParameter() {
        FollowUpDTO.FollowUpQuery query = parser.parse(params(Map.of(
                "facility_id", "FAC-0207", "status", "missed", "overdue_days", "14",
                "sort", "days_overdue_asc", "page", "3", "page_size", "25")));

        assertThat(query.getFacilityId()).isEqualTo("FAC-0207");
        assertThat(query.getStatus()).isEqualTo(FollowUpStatus.MISSED);
        assertThat(query.getOverdueDays()).isEqualTo(14);
        assertThat(query.getSort()).isEqualTo(SortOrder.DAYS_OVERDUE_ASC);
        assertThat(query.offset()).isEqualTo(50);
    }

    @Test
    void requiresAWellFormedFacility() {
        assertRejected(params(Map.of()), "facility_id is required");
        assertRejected(params(Map.of("facility_id", " ")), "facility_id is required");
        assertRejected(params(Map.of("facility_id", "FAC-0101' OR '1'='1")),
                "facility_id must look like FAC-0101");
    }

    @Test
    void rejectsBadValuesWithAMessageNamingTheField() {
        assertRejected(params(Map.of("facility_id", "FAC-0101", "status", "late")),
                "status must be one of due_soon, overdue, missed");
        assertRejected(params(Map.of("facility_id", "FAC-0101", "sort", "name")),
                "sort must be days_overdue_desc or days_overdue_asc");
        assertRejected(params(Map.of("facility_id", "FAC-0101", "overdue_days", "-1")),
                "overdue_days must be between 0 and 365");
        assertRejected(params(Map.of("facility_id", "FAC-0101", "overdue_days", "seven")),
                "overdue_days must be a whole number");
        assertRejected(params(Map.of("facility_id", "FAC-0101", "page", "0")),
                "page must be between 1 and 10000");
        assertRejected(params(Map.of("facility_id", "FAC-0101", "page_size", "201")),
                "page_size must be between 1 and 200");
    }

    @Test
    void rejectsUnknownAndRepeatedParametersInsteadOfIgnoringThem() {
        assertRejected(params(Map.of("facility", "FAC-0101")), "Unknown query parameter: facility");

        MultiValueMap<String, String> repeated = new LinkedMultiValueMap<>();
        repeated.put("facility_id", List.of("FAC-0101", "FAC-0207"));
        assertRejected(repeated, "facility_id must be given only once");
    }
}
