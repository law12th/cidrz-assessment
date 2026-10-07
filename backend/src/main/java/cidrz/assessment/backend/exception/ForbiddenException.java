package cidrz.assessment.backend.exception;

public class ForbiddenException extends BaseException {

    public ForbiddenException(String message) {
        super(message, "FORBIDDEN", 403);
    }
}
