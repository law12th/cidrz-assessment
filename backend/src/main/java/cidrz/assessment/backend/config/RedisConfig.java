package cidrz.assessment.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Lua scripts run server-side in Redis, so a check and its follow-up write are one atomic step.
 */
@Configuration
public class RedisConfig {

    /**
     * Fixed-window rate limiter. Increments a per-caller counter and sets the window TTL on the
     * first hit, returning the running count.
     *
     * <pre>
     * KEYS[1] — window counter key (e.g. carelink:rl:follow-up:clinician.fac0101)
     * ARGV[1] — window size in seconds
     * </pre>
     */
    @Bean
    public RedisScript<Long> rateLimitScript() {
        String script = """
                local current = redis.call("incr", KEYS[1])
                if current == 1 then
                    redis.call("expire", KEYS[1], ARGV[1])
                end
                return current
                """;
        return RedisScript.of(script, Long.class);
    }
}
