package com.itangcent.model.generic;

import java.io.Serializable;
import java.util.List;

/**
 * Base page model
 *
 * @param <T> the type of data items
 */
public class Page<T> implements Serializable {

    private static final long serialVersionUID = -3678174055538552725L;

    /**
     * page data
     */
    private List<T> data;

    /**
     * total count
     */
    private Integer totalCount;

    /**
     * current page number
     */
    private Integer currentPage;

    /**
     * page size
     */
    private Integer pageSize;

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public Integer getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Integer totalCount) {
        this.totalCount = totalCount;
    }

    public Integer getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(Integer currentPage) {
        this.currentPage = currentPage;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
