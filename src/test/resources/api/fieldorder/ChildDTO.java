package com.itangcent.fieldorder;

import com.itangcent.fieldorder.ParentDTO;

public class ChildDTO extends ParentDTO {

    private String childField;

    public String getChildField() { return childField; }
    public void setChildField(String childField) { this.childField = childField; }
}
