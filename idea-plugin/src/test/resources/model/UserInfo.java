package com.itangcent.model;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * user info
 */
public class UserInfo {

    private Long id;//user id

    /**
     * @see com.itangcent.common.constant.UserType
     */
    private int type;//user type

    /**
     * @mock tangcent
     */
    @NotBlank
    private String name;//user name

    /**
     * user age
     *
     * @mock 1${digit}
     */
    @NotNull
    private Integer age;

    /**
     * @deprecated It's a secret
     */
    private Integer sex;

    //user birthDay
    private LocalDate birthDay;

    //user regtime
    private LocalDateTime regtime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public Integer getSex() {
        return sex;
    }

    public void setSex(Integer sex) {
        this.sex = sex;
    }

    public LocalDate getBirthDay() {
        return birthDay;
    }

    public void setBirthDay(LocalDate birthDay) {
        this.birthDay = birthDay;
    }

    public LocalDateTime getRegtime() {
        return regtime;
    }

    public void setRegtime(LocalDateTime regtime) {
        this.regtime = regtime;
    }
}
