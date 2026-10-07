package cidrz.assessment.backend.redis.idempotency;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Remembers Idempotency-Keys that have already been written, so a client replaying a queued request
 * after a timeout — the Section D1 scenario — is answered without touching the database.
 *
 * <p><strong>Redis is an accelerator, not the authority.</strong> The authority is the unique
 * constraint on {@code contact_attempt.idempotency_key}: it is durable, it survives a Redis flush,
 * and it holds even if two replays land on two instances at the same moment. This cache only ever
 * turns a second write into a cheap read.
 *
 * <p>A key is recorded <em>after</em> the database write succeeds, never before. Marking first would
 * mean a failed insert leaves a key that suppresses the client's genuine retry, and the contact
 * attempt would be lost silently — the exact failure the brief's "no silent failures" rules out.
 *
 * <p>Fails open in both directions: a Redis error on read falls through to the database, and a
 * Redis error on write is logged and ignored.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContactAttemptIdempotencyCache {

    private static final String KEY_PREFIX = "carelink:idempotency:contact:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Boolean> isRecorded(String idempotencyKey) {
        return redisTemplate.hasKey(KEY_PREFIX + idempotencyKey)
                .onErrorResume(ex -> {
                    log.warn("Idempotency cache read failed — falling through to the database: {}",
                            ex.getMessage());
                    return Mono.just(false);
                });
    }

    public Mono<Void> markRecorded(String idempotencyKey, Duration ttl) {
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + idempotencyKey, "1", ttl)
                .onErrorResume(ex -> {
                    log.warn("Idempotency cache write failed — the unique constraint still holds: {}",
                            ex.getMessage());
                    return Mono.just(false);
                })
                .then();
    }
}
