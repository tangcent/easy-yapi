package com.itangcent.api.generic;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 1: Concrete controller binding T to String
 */
@RestController
@RequestMapping("/string")
public class StringCtrl extends GenericBaseCtrl<String> {
}
