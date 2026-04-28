package com.itangcent.model.generic;

import com.itangcent.model.generic.BaseResult;

/**
 * Extended base result with traceId
 *
 * @param <T> the type of content data
 */
public abstract class AtaBaseResult<T> extends BaseResult<T> {

    private static final long serialVersionUID = 1L;

    /**
     * trace id
     */
    private String traceId;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}
