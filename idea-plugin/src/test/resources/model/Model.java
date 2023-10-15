package com.itangcent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    private String shouldIgnore;

    private String shouldIgnoreByGetter;

    private String shouldIgnoreBySetter;

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

    public String getShouldIgnore() {
        return shouldIgnore;
    }

    public void setShouldIgnore(String shouldIgnore) {
        this.shouldIgnore = shouldIgnore;
    }

    @JsonIgnore
    public String getShouldIgnoreByGetter() {
        return shouldIgnoreByGetter;
    }

    public void setShouldIgnoreByGetter(String shouldIgnoreByGetter) {
        this.shouldIgnoreByGetter = shouldIgnoreByGetter;
    }

    public String getShouldIgnoreBySetter() {
        return shouldIgnoreBySetter;
    }

    @JsonIgnore
    public void setShouldIgnoreBySetter(String shouldIgnoreBySetter) {
        this.shouldIgnoreBySetter = shouldIgnoreBySetter;
    }
}