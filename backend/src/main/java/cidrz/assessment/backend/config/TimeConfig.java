package cidrz.assessment.backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * "Today" is the facility's local date, not the server's. A clock in Africa/Lusaka (UTC+2, no
 * daylight saving) keeps {@code days_overdue} correct when a request arrives between 00:00 and
 * 02:00 local time. Tests replace this bean with a fixed clock.
 */
@Configuration
@RequiredArgsConstructor
public class TimeConfig {

    private final ConfigProperties configProperties;

    @Bean
    public Clock clock() {
        return Clock.system(ZoneId.of(configProperties.getTimeZone()));
    }
}
