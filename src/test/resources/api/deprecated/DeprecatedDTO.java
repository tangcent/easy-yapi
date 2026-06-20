package com.itangcent.deprecated;

public class DeprecatedDTO {

    /**
     * Active field
     */
    private String activeField;

    /**
     * Old field
     *
     * @deprecated use activeField instead
     */
    @Deprecated
    private String oldField;

    public String getActiveField() { return activeField; }
    public void setActiveField(String activeField) { this.activeField = activeField; }
    public String getOldField() { return oldField; }
    public void setOldField(String oldField) { this.oldField = oldField; }
}
