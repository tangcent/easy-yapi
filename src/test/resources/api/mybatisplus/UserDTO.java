package com.itangcent.mybatisplus;

/**
 * DTO with an enum field annotated by @EnumValue.
 */
public class UserDTO {
    /** user id */
    private Long id;
    /** user type */
    private UserType type;
    /** user name */
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public UserType getType() { return type; }
    public void setType(UserType type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
