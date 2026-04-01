package com.itangcent.model;

import com.itangcent.model.Model;

class SubModel extends Model {

    private String subA;

    /**
     * @order 100
     */
    private String subShouldBeLast;

    private String subB;

    /**
     * @order 0
     */
    private String subShouldBeFirst;
}