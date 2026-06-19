package enumcases.model;

import enumcases.constant.SimpleStatus;
import enumcases.constant.UserType;
import enumcases.constant.JacksonStatus;
import enumcases.constant.MyBatisStatus;
import enumcases.constant.NameConflictEnum;

/**
 * Comprehensive DTO covering all enum resolution cases from the enum-resolution spec.
 *
 * Case 1 (field declared as enum type):
 * - simpleStatus: Case 1a — default name serialization (no annotation, no rule)
 * - jacksonStatus: Case 1b — @JsonValue on getter
 * - myBatisStatus: Case 1b — @EnumValue on field
 *
 * Case 2 (normal-typed field with @see):
 * - userCode: @see Enum#field (explicit field)
 * - userDesc: @see Enum#getField() (getter)
 * - constantName: @see Enum#name() (pseudo-field)
 * - autoMatchedCode: @see Enum (class-only, auto-match by type)
 */
public class ComprehensiveEnumCasesDto {

    // ================================================================
    //  Case 1a: enum-typed field, default name serialization
    // ================================================================
    public SimpleStatus simpleStatus;

    // ================================================================
    //  Case 1b: @JsonValue on getter
    // ================================================================
    public JacksonStatus jacksonStatus;

    // ================================================================
    //  Case 1b: @EnumValue on field
    // ================================================================
    public MyBatisStatus myBatisStatus;

    // ================================================================
    //  Case 2: @see Enum#field (explicit field)
    // ================================================================
    /**
     * @see UserType#code
     */
    public int userCode;

    // ================================================================
    //  Case 2: @see Enum#getField() (getter)
    // ================================================================
    /**
     * @see UserType#getDesc()
     */
    public String userDesc;

    // ================================================================
    //  Case 2: @see Enum#name() (pseudo-field)
    // ================================================================
    /**
     * @see NameConflictEnum#name()
     */
    public String constantName;

    // ================================================================
    //  Case 2: @see Enum (class-only, auto-match by type)
    // ================================================================
    /**
     * @see UserType
     */
    public int autoMatchedCode;
}
