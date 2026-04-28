package com.itangcent.model.generic;

import com.itangcent.model.generic.SwappedPair;

/**
 * Concrete: SwappedPair<Integer, String>
 *
 * Full chain:
 *   ConcreteSwappedPair extends SwappedPair<Integer, String>
 *   SwappedPair<X, Y> extends Pair<Y, X>
 *   Pair<A, B> { A first; B second; }
 *
 * So: X=Integer, Y=String
 *     Pair<Y, X> = Pair<String, Integer>
 *     first: A=Y=String
 *     second: B=X=Integer
 */
public class ConcreteSwappedPair extends SwappedPair<Integer, String> {
}
