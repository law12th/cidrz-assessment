package cidrz.assessment.backend.exception;

public class ResourceNotFoundException extends BaseException {

    /** House style: names the resource and the identifier. Safe for non-patient resources. */
    public ResourceNotFoundException(String resource, String id) {
        this(String.format("%s %s was not found", resource, id));
    }

    /**
     * For patient lookups, where naming the identifier would confirm that a patient exists at a
     * facility the caller cannot read. A patient at another facility and a patient that does not
     * exist must be indistinguishable.
     */
    public ResourceNotFoundException(String message) {
        super(message, "NOT_FOUND", 404);
    }
}
