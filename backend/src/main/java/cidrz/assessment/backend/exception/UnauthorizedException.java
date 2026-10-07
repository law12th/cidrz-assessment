package cidrz.assessment.backend.exception;

public class UnauthorizedException extends BaseException {

    public UnauthorizedException() {
        this("A valid bearer token is required");
    }

    public UnauthorizedException(String message) {
        super(message, "UNAUTHENTICATED", 401);
    }
}
