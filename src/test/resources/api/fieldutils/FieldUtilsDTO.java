package com.itangcent.fieldutils;

public class FieldUtilsDTO {

    private String normalField;

    private transient String transientField;

    private static final long serialVersionUID = 1L;

    public String publicField;

    public String getNormalField() { return normalField; }
    public void setNormalField(String normalField) { this.normalField = normalField; }
    public String getTransientField() { return transientField; }
    public void setTransientField(String transientField) { this.transientField = transientField; }
}
