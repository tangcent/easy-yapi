package com.itangcent.model.binding;

/**
 * Shared paging parameters: {@code page} and {@code rows} are writable,
 * {@code realPage} is computed and has no setter.
 */
public class PageParam {

    /** page number */
    private int page = 1;

    /** page size */
    private int rows = 10;

    public int getPage() {
        return page <= 0 ? 0 : (page - 1) * rows;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getRows() {
        return rows;
    }

    public void setRows(int rows) {
        this.rows = rows;
    }

    /** computed from page and rows */
    public int getRealPage() {
        return page <= 0 ? 1 : page / rows + 1;
    }
}
