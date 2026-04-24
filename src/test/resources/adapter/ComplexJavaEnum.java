package com.test.adapter;

/**
 * HTTP status codes as an enum.
 * @author JavaDev
 * @since 1.0
 */
public enum JavaComplexEnum {
    /** Success. */
    OK(200, "OK"),
    /** Not found. */
    NOT_FOUND(404, "Not Found"),
    /** Server error. */
    INTERNAL_ERROR(500, "Internal Server Error");

    /** The numeric status code. */
    private final int code;
    /** The reason phrase. */
    private final String reason;

    JavaComplexEnum(int code, String reason) {
        this.code = code;
        this.reason = reason;
    }

    /**
     * Gets the numeric status code.
     * @return the status code
     */
    public int getCode() { return code; }

    /**
     * Gets the reason phrase.
     * @return the reason phrase
     */
    public String getReason() { return reason; }

    /**
     * Checks if this is a success status.
     * @return true if code is in 2xx range
     */
    public boolean isSuccess() { return code >= 200 && code < 300; }
}
