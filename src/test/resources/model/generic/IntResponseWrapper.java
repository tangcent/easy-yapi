package com.itangcent.model.generic;

import com.itangcent.model.generic.ResponseWrapper;

/**
 * Concrete: ResponseWrapper<Integer>
 *
 * Full chain:
 *   IntResponseWrapper extends ResponseWrapper<Integer>
 *   ResponseWrapper<T> extends Pair<String, Wrapper<T>>
 *   Pair<A, B> { A first; B second; }
 *   Wrapper<T> { T value; String label; }
 *
 * So: first: A=String
 *     second: B=Wrapper<Integer> (object with value:Integer, label:String)
 */
public class IntResponseWrapper extends ResponseWrapper<Integer> {
}
