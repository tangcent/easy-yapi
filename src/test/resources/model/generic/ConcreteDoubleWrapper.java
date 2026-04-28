package com.itangcent.model.generic;

import com.itangcent.model.generic.DoubleWrapper;

/**
 * Concrete class: DoubleWrapper<String>
 *
 * Full chain:
 *   ConcreteDoubleWrapper extends DoubleWrapper<String>
 *   DoubleWrapper<T> extends Wrapper<Wrapper<T>>
 *   Wrapper<T> { T value; String label; }
 *
 * So `value` should resolve to Wrapper<String> (an object with value:String, label:String)
 */
public class ConcreteDoubleWrapper extends DoubleWrapper<String> {
}
