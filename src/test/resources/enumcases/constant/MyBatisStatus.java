package enumcases.constant;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * Enum with @EnumValue on field.
 * Used to test Case 1b: value field via framework annotation.
 */
public enum MyBatisStatus {
    /** guest */
    GUEST(10),
    /** admin */
    ADMIN(20),
    /** developer */
    DEVELOPER(30);

    @EnumValue
    private final Integer code;

    MyBatisStatus(Integer code) {
        this.code = code;
    }

    public Integer getCode() { return code; }
}
