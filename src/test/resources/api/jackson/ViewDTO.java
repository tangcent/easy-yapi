package com.itangcent.jackson;

import com.fasterxml.jackson.annotation.JsonView;
import com.itangcent.jackson.JsonViewViews;

public class ViewDTO {

    @JsonView(JsonViewViews.Public.class)
    private Long id;

    @JsonView(JsonViewViews.Public.class)
    private String name;

    @JsonView(JsonViewViews.Internal.class)
    private String email;

    @JsonView(JsonViewViews.Admin.class)
    private String password;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
