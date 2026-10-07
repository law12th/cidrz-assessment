package cidrz.assessment.backend.exception;

/**
 * Input the API will not accept. The message is written for a clinician and is returned verbatim,
 * so it must never echo the rejected value back.
 *
 * <p>The kwasila {@code ValidationException} also carries a {@code fieldErrors} map; the CareLink
 * error contract has no slot for one, so the message carries the whole explanation.
 */
public class ValidationException extends BaseException {

    public ValidationException(String message) {
        super(message, "VALIDATION_FAILED", 400);
    }
}
