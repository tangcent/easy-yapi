package com.itangcent.model.generic;

import com.itangcent.model.generic.InverseE;

/**
 * ConcreteInverseE extends E<Long, Boolean>
 *
 * Full resolution:
 *   ConcreteInverseE extends E<M=Long, N=Boolean>
 *   E<M=Long, N=Boolean> extends D<N, M> = D<Boolean, Long>
 *   D<P=Boolean, Q=Long> extends A<Q, P> = A<Long, Boolean>
 *
 * Double inversion cancels out:
 *   E level:  M=Long, N=Boolean → m:Long, n:Boolean
 *   D level:  P=N=Boolean, Q=M=Long → p:Boolean, q:Long
 *   A level:  T=Q=M=Long, R=P=N=Boolean → t:Long, r:Boolean
 */
public class ConcreteInverseE extends InverseE<Long, Boolean> {
}
