package com.itangcent.model.generic;

import com.itangcent.model.generic.AtaBaseResult;
import com.itangcent.model.generic.AtaPage;

/**
 * Page result wrapper - wraps T in AtaPage before passing to parent.
 * AtaPageResult<T> extends AtaBaseResult<AtaPage<T>>
 * which means content field should be AtaPage<T>, not T directly.
 *
 * @param <T> the type of page data items
 */
public class AtaPageResult<T> extends AtaBaseResult<AtaPage<T>> {

    private static final long serialVersionUID = 1L;

    /**
     * whether there is a next page
     */
    private Boolean hasNextPage;

    @Override
    public AtaPage<T> getContent() {
        return super.content;
    }

    @Override
    public void setContent(AtaPage<T> content) {
        super.content = content;
    }

    public Boolean getHasNextPage() {
        return hasNextPage;
    }

    public void setHasNextPage(Boolean hasNextPage) {
        this.hasNextPage = hasNextPage;
    }
}
