package enumcases.constant;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Enum with @JsonValue on getter.
 * Used to test Case 1b: value field via framework annotation.
 */
public enum JacksonStatus {
    /** guest */
    GUEST(30),
    /** admin */
    ADMIN(1100),
    /** developer */
    DEVELOPER(1200);

    private final Integer code;

    JacksonStatus(Integer code) {
        this.code = code;
    }

    @JsonValue
    public Integer getCode() { return code; }
}
