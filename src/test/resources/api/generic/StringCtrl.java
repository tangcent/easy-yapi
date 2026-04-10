package com.itangcent.api.generic;

import com.itangcent.api.generic.GenericBaseCtrl;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 1: Concrete controller binding T to String
 */
@RestController
@RequestMapping("/string")
public class StringCtrl extends GenericBaseCtrl<String> {
}
