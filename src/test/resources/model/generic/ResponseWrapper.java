package com.itangcent.model.generic;

import com.itangcent.model.generic.Pair;
import com.itangcent.model.generic.Wrapper;

/**
 * Partially binds Pair: fixes A to String, wraps T in Wrapper<T> for B.
 * ResponseWrapper<T> extends Pair<String, Wrapper<T>>
 *
 * @param <T> the response data type
 */
public class ResponseWrapper<T> extends Pair<String, Wrapper<T>> {

    /**
     * response status
     */
    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
