package com.itangcent.validation.javax.strict;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.NotBlank;
import com.itangcent.validation.javax.strict.CreateGroup;

public class JavaxStrictUserDTO {

    @NotNull
    private Long id;

    @NotBlank(groups = CreateGroup.class)
    private String name;

    @NotNull
    private String email;

    private String address;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
