package com.itangcent.model.binding;

import com.itangcent.model.binding.PageParam;
import com.itangcent.model.binding.QueryFilter;

/**
 * Query object mixing writable and read-only properties.
 */
public class PageQuery extends PageParam {

    /** writable through its setter */
    private String name;

    /** read-only: getter, no setter */
    private String token;

    /** public mutable field */
    public String keyword;

    /** nested object, writable through its setter */
    private QueryFilter filter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getToken() {
        return token;
    }

    public QueryFilter getFilter() {
        return filter;
    }

    public void setFilter(QueryFilter filter) {
        this.filter = filter;
    }

    /** read-only: computed, no field and no setter */
    public String getDisplayName() {
        return name + "@" + keyword;
    }
}
