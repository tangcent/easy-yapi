package com.itangcent.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

class Model {

    /**
     * string field
     */
    @JsonProperty("s")
    private String str;

    /**
     * integer field
     */
    private Integer integer;

    /**
     * stringList field
     */
    private List<String> stringList;

    /**
     * integerArray field
     */
    private Integer[] integerArray;

    public String getStr() {
        return str;
    }

    public void setStr(String str) {
        this.str = str;
    }

    public Integer getInteger() {
        return integer;
    }

    public void setInteger(Integer integer) {
        this.integer = integer;
    }

    public List<String> getStringList() {
        return stringList;
    }

    public void setStringList(List<String> stringList) {
        this.stringList = stringList;
    }

    public Integer[] getIntegerArray() {
        return integerArray;
    }

    public void setIntegerArray(Integer[] integerArray) {
        this.integerArray = integerArray;
    }

    public void setOnlySet(String onlySet) {

    }

    public String getOnlyGet() {

    }
}