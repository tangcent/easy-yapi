package com.itangcent.cases;

import com.itangcent.model.*;
import com.itangcent.cases.NestedClass.*;
import com.itangcent.cases.NestedClassB.InnerClassB;

import static com.itangcent.cases.NestedClassB.StaticInnerClassB;
import static com.itangcent.constant.Numbers.ONE;
import static com.itangcent.constant.JavaVersion.JAVA_0_9;

public class LinkCase {

    /**
     * @return {@link UserInfo}
     * @see {@link UserInfo}
     */
    public void methodA() {
    }

    /**
     * @return {@link MyInnerClass}
     * @see {@link MyInnerClass}
     */
    public void methodB() {
    }

    /**
     * @return {@link InnerClassA}
     * @see {@link StaticInnerClassA}
     */
    public void methodC() {
    }

    /**
     * @return {@link InnerClassB}
     * @see {@link StaticInnerClassB}
     */
    public void methodD() {
    }

    /**
     * @return {@link com.itangcent.model.Model}
     * @see {@link com.itangcent.model.Model}
     */
    public void methodE() {
    }

    /**
     * @return {@link JAVA_0_9}
     * @see {@link ONE}
     */
    public void methodF() {
    }

    /**
     * @return {@link UserInfo#getId}
     * @see {@link com.itangcent.model.Model#getStr()}
     */
    public void methodG() {
    }

    /**
     * @return {@link UserInfo#id}
     * @see {@link com.itangcent.model.Model#str}
     */
    public void methodH() {
    }

    private class MyInnerClass {

    }
}
