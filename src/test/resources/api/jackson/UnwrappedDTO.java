package com.itangcent.jackson;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.itangcent.jackson.AddressDTO;

public class UnwrappedDTO {

    private String name;

    @JsonUnwrapped
    private AddressDTO address;

    @JsonUnwrapped(prefix = "home_", suffix = "_addr")
    private AddressDTO homeAddress;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public AddressDTO getAddress() { return address; }
    public void setAddress(AddressDTO address) { this.address = address; }
    public AddressDTO getHomeAddress() { return homeAddress; }
    public void setHomeAddress(AddressDTO homeAddress) { this.homeAddress = homeAddress; }
}
