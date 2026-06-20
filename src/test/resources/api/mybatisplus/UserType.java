package com.itangcent.mybatisplus;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * User type enum with @EnumValue on the code field.
 * MyBatis-Plus serializes this enum by the code field's value.
 */
public enum UserType {
    /** who is not logged in */
    GUEST(30, "unspecified"),
    /** system manager */
    ADMIN(1100, "administrator"),
    /** developer that designs this app */
    DEVELOPER(1200, "developer");

    @EnumValue
    private final Integer code;
    private final String desc;

    UserType(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public Integer getCode() { return code; }
    public String getDesc() { return desc; }
}
