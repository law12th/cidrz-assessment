package cidrz.assessment.backend.redis.ratelimit;

import cidrz.assessment.backend.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Redis-backed fixed-window rate limiter, as in the kwasila services. Counts hits against a
 * caller-supplied key within a window; once the count exceeds the maximum the call errors with
 * {@link RateLimitExceededException} (HTTP 429) until the window rolls over.
 *
 * <p>Here it protects the database from a client bug rather than from an attacker: the front-end
 * extract in the brief's Section C polls the endpoint every three seconds, and that pattern across a
 * few hundred clinics is what takes the national node down. The limit is per principal, so one
 * misbehaving browser cannot crowd out a whole facility.
 *
 * <p><strong>Fails open.</strong> If Redis is unreachable the request is allowed through. A clinic
 * that cannot see its follow-up list because a cache is down is a worse outcome than an unthrottled
 * request, and the facility nodes are explicitly expected to lose connectivity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    public Mono<Void> enforce(String key, int maxRequests, Duration window) {
        long windowSeconds = Math.max(1, window.toSeconds());
        return redisTemplate
                .execute(rateLimitScript, List.of(key), List.of(String.valueOf(windowSeconds)))
                .next()
                .defaultIfEmpty(0L)
                .flatMap(count -> {
                    if (count > maxRequests) {
                        log.debug("Rate limit exceeded for key='{}' ({} > {})", key, count, maxRequests);
                        return Mono.error(new RateLimitExceededException(
                                "Too many requests — please slow down and try again shortly."));
                    }
                    return Mono.<Void>empty();
                })
                .onErrorResume(ex -> {
                    if (ex instanceof RateLimitExceededException) {
                        return Mono.error(ex);
                    }
                    log.warn("Rate limiter unavailable ({}); allowing request for key='{}'",
                            ex.getMessage(), key);
                    return Mono.empty();
                });
    }
}
