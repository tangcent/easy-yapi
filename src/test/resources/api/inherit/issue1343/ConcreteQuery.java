package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.IQuery;

/**
 * Concrete query type implementing IQuery.
 */
public class ConcreteQuery implements IQuery {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
