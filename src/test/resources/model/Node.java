package com.itangcent.model;

import java.util.List;

public class Node {

    /**
     * primary key
     */
    private String id;

    /**
     * node code
     */
    private String code;

    private Node parent;

    /**
     * sub nodes
     */
    private List<Node> sub;

    /**
     * siblings nodes
     */
    private List<Node> siblings;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public com.itangcent.model.Node getParent() {
        return parent;
    }

    public void setParent(com.itangcent.model.Node parent) {
        this.parent = parent;
    }

    public List<com.itangcent.model.Node> getSub() {
        return sub;
    }

    public void setSub(List<com.itangcent.model.Node> sub) {
        this.sub = sub;
    }

    public List<com.itangcent.model.Node> getSiblings() {
        return siblings;
    }

    public void setSiblings(List<com.itangcent.model.Node> siblings) {
        this.siblings = siblings;
    }
}
