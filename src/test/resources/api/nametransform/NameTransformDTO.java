package com.itangcent.nametransform;

public class NameTransformDTO {

    /** Public field generated alongside getBarcode(), as reported in #1492. */
    public String Barcode;

    public String getBarcode() {
        return Barcode;
    }

    /** Delimiter-separated name, used by the camel-case transform. */
    public String user_name;

    /** camelCase name, used by the snake-case and kebab-case transforms. */
    public String displayName;

    /** Acronym name, used to pin the Jackson strategy difference. */
    public String URLValue;

    /** Setter-only property, with no backing field and no getter. */
    public void setOnlySetter(String value) {
    }
}
