package com.itangcent.api.generic;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 2: Fully concrete controller binding X to Long
 * MiddleCtrl<X> extends TwoTypeBaseCtrl<X, String>
 * ConcreteCtrl extends MiddleCtrl<Long>
 * → T=Long, R=String
 */
@RestController
@RequestMapping("/concrete")
public class ConcreteCtrl extends MiddleCtrl<Long> {
}
