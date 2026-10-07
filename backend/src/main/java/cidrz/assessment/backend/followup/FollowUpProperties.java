package cidrz.assessment.backend.followup;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The tunable numbers behind the follow-up rule.
 *
 * <p>These are programme decisions, not engineering decisions. How long a patient may be late before
 * a clinic chases them is something the Ministry changes; it should not need a release, a deployment
 * window, or an engineer. They live in configuration, and they are validated here so a nonsensical
 * combination fails at startup rather than quietly returning the wrong list all morning.
 */
@ConfigurationProperties(prefix = "carelink.follow-up")
public record FollowUpProperties(
        int defaultOverdueDays,
        int dueSoonDays,
        int recentWindowDays,
        int defaultPageSize,
        int maxPageSize) {

    public FollowUpProperties {
        if (defaultOverdueDays < 0 || dueSoonDays < 0 || recentWindowDays < 1
                || defaultPageSize < 1 || maxPageSize < defaultPageSize) {
            throw new IllegalStateException("Invalid carelink.follow-up configuration");
        }
    }
}
