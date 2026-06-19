package enumcases.constant;

/**
 * Enum with multiple instance fields (code, desc).
 * Used to test Case 2 (@see) and auto-match by type.
 */
public enum UserType {
    /** guest */
    GUEST(30, "unspecified"),
    /** admin */
    ADMIN(1100, "administrator"),
    /** developer */
    DEVELOPER(1200, "developer");

    private final Integer code;
    private final String desc;

    UserType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }
}
