package com.itangcent.validation.jakarta.strict;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import com.itangcent.validation.jakarta.strict.CreateGroup;

public class JakartaStrictUserDTO {

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
