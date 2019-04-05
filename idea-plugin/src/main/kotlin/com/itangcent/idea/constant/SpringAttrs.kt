package com.itangcent.idea.constant

object SpringAttrs {

    val SPRING_REQUEST_RESPONSE: Array<String> = arrayOf("HttpServletRequest", "HttpServletResponse")

    var SPRING_CONTROLLER_ANNOTATION: Set<String> =
            mutableSetOf("org.springframework.stereotype.Controller",
                    "org.springframework.web.bind.annotation.RestController")
}