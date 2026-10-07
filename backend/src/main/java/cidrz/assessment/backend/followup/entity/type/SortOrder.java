package cidrz.assessment.backend.followup.entity.type;

import java.util.Arrays;
import java.util.Optional;

/**
 * The sort orders the contract allows. A closed set, not a free-text column name: the direction
 * cannot be bound as a parameter, so an open-ended sort parameter would mean building SQL from
 * caller input.
 */
public enum SortOrder {

    DAYS_OVERDUE_DESC("days_overdue_desc"),
    DAYS_OVERDUE_ASC("days_overdue_asc");

    private final String wireValue;

    SortOrder(String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static Optional<SortOrder> fromWire(String value) {
        return Arrays.stream(values()).filter(order -> order.wireValue.equals(value)).findFirst();
    }
}
