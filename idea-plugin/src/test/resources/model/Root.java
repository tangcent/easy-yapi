package com.itangcent.model;

import com.itangcent.model.Node;

import java.util.List;

public class Root {

    /**
     * primary key
     */
    private String id;

    /**
     * sub nodes
     */
    private List<Node> children;

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public List<Node> getChildren() {
        return children;
    }

    public void setChildren(List<Node> children) {
        this.children = children;
    }
}
