package com.itangcent.model.generic;

import com.itangcent.model.generic.InverseB;

/**
 * C extends B<String, Integer>
 *
 * Full resolution:
 *   C extends B<String, Integer>
 *   B<X=String, Y=Integer> extends A<Y, X> = A<Integer, String>
 *
 * So at B level:  X=String, Y=Integer → x:String, y:Integer
 * So at A level:  T=Y=Integer, R=X=String → t:Integer, r:String
 */
public class InverseC extends InverseB<String, Integer> {
}
