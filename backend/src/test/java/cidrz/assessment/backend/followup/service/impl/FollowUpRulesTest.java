package cidrz.assessment.backend.followup.service.impl;

import cidrz.assessment.backend.followup.entity.type.FollowUpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule's boundaries, with the defaults: threshold 7, due-soon window 7, recent window 14.
 * The boundaries are where the brief is ambiguous, so they are what is worth testing exhaustively.
 */
class FollowUpRulesTest {

    private final FollowUpRules rules = new FollowUpRules(7, 14);

    @ParameterizedTest(name = "days_overdue {0} -> {1}")
    @CsvSource({
            "-8,  NONE",      // appointment more than a week away: not on any list yet
            "-7,  DUE_SOON",  // first day of the due-soon window
            "0,   DUE_SOON",  // appointment is today
            "1,   NONE",      // one day late: grace period
            "7,   NONE",      // exactly n days: the brief says MORE than n, so not yet listed
            "8,   MISSED",    // first day on the list
            "21,  MISSED",    // last day of the recently missed window (n + 14)
            "22,  OVERDUE",   // first day overdue
            "400, OVERDUE"
    })
    void classifiesEachSideOfEveryBoundary(int daysOverdue, String expected) {
        Optional<FollowUpStatus> status = rules.classify(daysOverdue, 7);
        if (expected.equals("NONE")) {
            assertThat(status).isEmpty();
        } else {
            assertThat(status).contains(FollowUpStatus.valueOf(expected));
        }
    }

    @Test
    void noStatusFilterMeansTheBriefsRuleStrictlyMoreThanNDays() {
        assertThat(rules.rangeFor(null, 7)).isEqualTo(new FollowUpRules.DayRange(8, Integer.MAX_VALUE));
    }

    @Test
    void statusRangesFollowTheThreshold() {
        assertThat(rules.rangeFor(FollowUpStatus.DUE_SOON, 7)).isEqualTo(new FollowUpRules.DayRange(-7, 0));
        assertThat(rules.rangeFor(FollowUpStatus.MISSED, 7)).isEqualTo(new FollowUpRules.DayRange(8, 21));
        assertThat(rules.rangeFor(FollowUpStatus.OVERDUE, 7))
                .isEqualTo(new FollowUpRules.DayRange(22, Integer.MAX_VALUE));
        assertThat(rules.rangeFor(FollowUpStatus.MISSED, 30)).isEqualTo(new FollowUpRules.DayRange(31, 44));
    }

    @Test
    void zeroThresholdListsAPatientTheDayAfterTheAppointment() {
        assertThat(rules.classify(1, 0)).contains(FollowUpStatus.MISSED);
        assertThat(rules.rangeFor(null, 0).minDays()).isEqualTo(1);
    }

    /**
     * The SQL selects a range and the rule then labels each row. If the two ever disagree a patient
     * is labelled with a status the filter did not ask for, so the agreement is asserted directly.
     */
    @ParameterizedTest(name = "threshold {0}")
    @CsvSource({"0", "7", "30", "365"})
    void everyDayInsideAFilterRangeClassifiesAsThatStatus(int threshold) {
        for (FollowUpStatus status : FollowUpStatus.values()) {
            FollowUpRules.DayRange range = rules.rangeFor(status, threshold);
            int upper = Math.min(range.maxDays(), range.minDays() + 500);
            for (int day = range.minDays(); day <= upper; day++) {
                assertThat(rules.classify(day, threshold)).as("day=%d", day).contains(status);
            }
        }
    }
}
