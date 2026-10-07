package cidrz.assessment.backend.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * House-style holder for the single-valued settings, as in every kwasila service.
 *
 * <p>Settings that are a map, or that need validating as a group, stay as typed
 * {@code @ConfigurationProperties} records next to the code that reads them
 * ({@code auth.AuthProperties}, {@code followup.FollowUpProperties}): {@code @Value} cannot bind a
 * map of tokens, and cannot reject an invalid combination of page sizes at startup.
 */
@Getter
@Configuration
public class ConfigProperties {

    /** The facility's time zone. "Today" is a local date, never the server's. */
    @Value("${carelink.time-zone}")
    private String timeZone;

    /** Requests allowed per caller, per endpoint, within {@link #rateLimitWindow}. */
    @Value("${carelink.rate-limit.max-requests}")
    private int rateLimitMaxRequests;

    @Value("${carelink.rate-limit.window}")
    private Duration rateLimitWindow;

    /** How long a replayed Idempotency-Key is answered from Redis rather than the database. */
    @Value("${carelink.idempotency.ttl}")
    private Duration idempotencyTtl;
}
