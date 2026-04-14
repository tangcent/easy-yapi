package com.itangcent.yapi

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Deprecated("Use NewKotlinController instead")
@RestController
@RequestMapping("/kotlin/deprecated")
class DeprecatedKotlinController {

    /**
     * Old method
     *
     * @return Returns old data
     */
    @GetMapping("/old")
    fun oldMethod(): String {
        return "old data"
    }
}

@RestController
@RequestMapping("/kotlin/new")
class NewKotlinController {

    /**
     * New method
     *
     * @open
     * @return Returns new data
     */
    @GetMapping("/new")
    fun newMethod(): String {
        return "new data"
    }
}
