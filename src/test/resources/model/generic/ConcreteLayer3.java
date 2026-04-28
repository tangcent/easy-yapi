package com.itangcent.model.generic;

import com.itangcent.model.generic.Layer3;

/**
 * Concrete: Layer3<Integer>
 *
 * Full resolution chain:
 *   ConcreteLayer3 extends Layer3<Integer>
 *   Layer3<T=Integer> extends Layer2<Pair<String, Integer>>
 *   Layer2<T=Pair<String, Integer>> extends Layer1<Wrapper<Pair<String, Integer>>>
 *   Layer1<T=Wrapper<Pair<String, Integer>>> { T value; }
 *
 * So value = Wrapper<Pair<String, Integer>>
 *   Wrapper.value = Pair<String, Integer>
 *     Pair.first = String
 *     Pair.second = Integer
 *   Wrapper.label = String
 */
public class ConcreteLayer3 extends Layer3<Integer> {
}
