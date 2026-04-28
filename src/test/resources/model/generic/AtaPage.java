package com.itangcent.model.generic;

import com.itangcent.model.generic.Page;

/**
 * Extended page with hasNextPage
 *
 * @param <T> the type of data items
 */
public class AtaPage<T> extends Page<T> {

    /**
     * whether there is a next page
     */
    private Boolean hasNextPage;

    public Boolean getHasNextPage() {
        return hasNextPage;
    }

    public void setHasNextPage(Boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }
}
