package com.itangcent.constant;

/**
 * type of user
 */
public enum UserType {
    //administration
    ADMIN(1, "ADMINISTRATION"),

    //a person, an animal or a plant
    MEM(2, "MEMBER"),

    //Anonymous visitor
    GUEST(3, "ANONYMOUS");

    private int type;//user type

    private String desc;

    public int getType() {
        return type;
    }

    public String getDesc() {
        return desc;
    }

    UserType(int type, String desc) {
        this.type = type;
        this.desc = desc;
    }
}
