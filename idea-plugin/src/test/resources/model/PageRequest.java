package com.itangcent.model;

import com.itangcent.model.UserInfo;

import java.util.List;

public class PageRequest<T> {

    private String size;

    private UserInfo user;

    private List<UserInfo> users;

    private T t;

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public UserInfo getUser() {
        return user;
    }

    public void setUser(UserInfo user) {
        this.user = user;
    }

    public List<UserInfo> getUsers() {
        return users;
    }

    public void setUsers(List<UserInfo> users) {
        this.users = users;
    }

    public T getT() {
        return t;
    }

    public void setT(T t) {
        this.t = t;
    }
}
