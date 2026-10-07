package cidrz.assessment.backend.followup.entity.type;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Optional;

/** The three statuses the brief defines. The wire value is snake_case; the constant is not. */
public enum FollowUpStatus {

    /** The appointment is today or within the next week. */
    DUE_SOON("due_soon"),

    /** Recently missed: past the threshold, but still inside the recent window. */
    MISSED("missed"),

    /** Missed some time ago. */
    OVERDUE("overdue");

    private final String wireValue;

    FollowUpStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String getWireValue() {
        return wireValue;
    }

    public static Optional<FollowUpStatus> fromWire(String value) {
        return Arrays.stream(values()).filter(status -> status.wireValue.equals(value)).findFirst();
    }
}
