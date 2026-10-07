package cidrz.assessment.backend.followup.service.impl;

import cidrz.assessment.backend.followup.FollowUpProperties;
import cidrz.assessment.backend.followup.entity.type.FollowUpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The follow-up rule, as pure functions — no database, no clock, no Spring. That is what makes the
 * boundaries cheap to test exhaustively, and the boundaries are where this rule is actually
 * contentious.
 *
 * <p>{@code days_overdue} is today minus the appointment date set by the patient's most recent
 * visit, so it is negative for an appointment still in the future. With threshold {@code n}
 * ({@code overdue_days}):
 *
 * <pre>
 *   days_overdue in [-dueSoonDays, 0]        due_soon   (appointment today or within the next week)
 *   days_overdue in [1, n]                   not listed (grace period: possibly just running late)
 *   days_overdue in [n + 1, n + window]      missed     ("recently missed")
 *   days_overdue &gt;= n + window + 1           overdue    (missed some time ago)
 * </pre>
 *
 * <p>"More than n days in the past" is read strictly: exactly {@code n} days is not yet listed. The
 * brief leaves this open and the README records the choice.
 *
 * <p>The SQL filters on exactly these ranges and every returned row is classified here, so the two
 * cannot drift apart silently — {@code FollowUpServiceImpl} fails loudly if they ever do.
 */
@Component
public class FollowUpRules {

    /** Inclusive bounds on {@code days_overdue}. {@code Integer.MAX_VALUE} means no upper bound. */
    public record DayRange(int minDays, int maxDays) {
    }

    private final int dueSoonDays;
    private final int recentWindowDays;

    @Autowired
    public FollowUpRules(FollowUpProperties properties) {
        this(properties.dueSoonDays(), properties.recentWindowDays());
    }

    /** Direct constructor, so the rule can be unit-tested without a Spring context. */
    public FollowUpRules(int dueSoonDays, int recentWindowDays) {
        this.dueSoonDays = dueSoonDays;
        this.recentWindowDays = recentWindowDays;
    }

    /**
     * The {@code days_overdue} range a status filter selects. No status means the brief's default:
     * missed and overdue together.
     */
    public DayRange rangeFor(FollowUpStatus status, int overdueDays) {
        if (status == null) {
            return new DayRange(overdueDays + 1, Integer.MAX_VALUE);
        }
        return switch (status) {
            case DUE_SOON -> new DayRange(-dueSoonDays, 0);
            case MISSED -> new DayRange(overdueDays + 1, overdueDays + recentWindowDays);
            case OVERDUE -> new DayRange(overdueDays + recentWindowDays + 1, Integer.MAX_VALUE);
        };
    }

    /** The status for a {@code days_overdue} value, or empty if the patient belongs on no list. */
    public Optional<FollowUpStatus> classify(int daysOverdue, int overdueDays) {
        if (daysOverdue >= -dueSoonDays && daysOverdue <= 0) {
            return Optional.of(FollowUpStatus.DUE_SOON);
        }
        if (daysOverdue <= overdueDays) {
            return Optional.empty();
        }
        if (daysOverdue <= overdueDays + recentWindowDays) {
            return Optional.of(FollowUpStatus.MISSED);
        }
        return Optional.of(FollowUpStatus.OVERDUE);
    }
}
