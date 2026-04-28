package com.itangcent.model.generic;

import java.io.Serializable;

/**
 * Base result wrapper
 *
 * @param <D> the type of content data
 */
public abstract class BaseResult<D> implements Serializable {

    private static final long serialVersionUID = 2360806571162643908L;

    /**
     * whether the request was successful
     */
    private boolean success;

    /**
     * error code
     */
    private String errorCode;

    /**
     * error message
     */
    private String errorMsg;

    /**
     * the content data
     */
    protected D content;

    public BaseResult() {
        this.success = true;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public D getContent() {
        return content;
    }

    public void setContent(D content) {
        this.content = content;
    }
}
