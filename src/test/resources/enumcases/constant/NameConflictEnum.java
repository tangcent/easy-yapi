package enumcases.constant;

/**
 * Enum with an instance field literally named `name` (issue #1383).
 * Used to test @see Enum#name() pseudo-field disambiguation.
 */
public enum NameConflictEnum {
    /** first */
    ONE(1),
    /** second */
    TWO(2),
    /** third */
    THREE(3);

    private final Integer name;

    NameConflictEnum(Integer name) {
        this.name = name;
    }

    public Integer getName() { return name; }
}
