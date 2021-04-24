package com.itangcent.model;

/**
 * A demo class for test comments
 *
 * @author tangcent
 */
public class CommentDemo {

    /**
     * single line
     *
     * @single
     * @desc low case of A
     */
    private String a;

    /**
     * multi-line
     * second line
     *
     * @multi
     * @module x
     * x1 x2 x3
     * @module y
     */
    private String b;

    /**
     * head line
     * second line
     * <pre>
     *     {
     *         "a":"b",
     *         "c":{
     *              "x":["y"]
     *         }
     *     }
     * </pre>
     * see @{link somelink}
     * tail line
     */
    private String c;

    /**
     * head line
     * second line
     * <pre>
     *
     *     {
     *         "a":"b",
     *         "c":{
     *              "x":["y"]
     *         }
     *     }
     *
     * </pre>
     * <p>
     * see @{link somelink}
     * tail line
     */
    private String d;

    private String e;//E is a mathematical constant approximately equal to 2.71828

    /**
     * R, or r, is the eighteenth letter of the modern English alphabet and the ISO basic Latin alphabet.
     */
    private String r;//It's before s

    /**
     * @param a A
     * @param b B
     */
    private String methodA(int a, int b) {

    }
}