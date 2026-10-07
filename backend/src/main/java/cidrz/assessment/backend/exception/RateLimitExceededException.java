package cidrz.assessment.backend.exception;

/**
 * Thrown when a caller exceeds the allowed request rate for an endpoint.
 * Maps to HTTP 429 via {@link GlobalExceptionHandler}.
 */
public class RateLimitExceededException extends BaseException {

    public RateLimitExceededException(String message) {
        super(message, "RATE_LIMIT_EXCEEDED", 429);
    }
}
