package cidrz.assessment.backend.exception;

import lombok.Getter;

/**
 * An expected failure, carrying the stable machine-readable code and the HTTP status it maps to.
 *
 * <p>Same shape as the kwasila {@code BaseException}, with one change: no stack trace is captured.
 * These are outcomes the API reports, not bugs, and at 3,000 concurrent users on weekday mornings a
 * stack trace per rejected request is pure cost. Unexpected failures still arrive with theirs intact
 * and are logged in full by {@link GlobalExceptionHandler}.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    protected BaseException(String message, String errorCode, int httpStatus) {
        super(message, null, false, false);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}
