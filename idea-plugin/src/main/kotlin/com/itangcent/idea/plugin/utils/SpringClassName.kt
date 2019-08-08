package com.itangcent.idea.plugin.utils


object SpringClassName {

    val SPRING_REQUEST_RESPONSE: Array<String> = arrayOf("HttpServletRequest", "HttpServletResponse")

    var SPRING_CONTROLLER_ANNOTATION: Set<String> =
            mutableSetOf("org.springframework.stereotype.Controller",
                    "org.springframework.web.bind.annotation.RestController")


    //file
    const val MULTIPARTFILE = "org.springframework.web.multipart.MultipartFile"

    //annotations
    const val REQUESTMAPPING_ANNOTATION = "org.springframework.web.bind.annotation.RequestMapping"
    const val REQUESTBOODY_ANNOTATION = "org.springframework.web.bind.annotation.RequestBody"
    const val REQUESTPARAM_ANNOTATION = "org.springframework.web.bind.annotation.RequestParam"
    const val MODELATTRIBUTE_ANNOTATION = "org.springframework.web.bind.annotation.ModelAttribute"
    const val PATHVARIABLE_ANNOTATION = "org.springframework.web.bind.annotation.PathVariable"

    const val REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader"

    const val GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping"
    const val POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping"
    const val PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping"
    const val DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping"
    const val PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping"

    val SPRING_REQUEST_MAPPING_ANNOTATIONS: Set<String> = setOf(SpringClassName.REQUESTMAPPING_ANNOTATION,
            SpringClassName.GET_MAPPING,
            SpringClassName.DELETE_MAPPING,
            SpringClassName.PATCH_MAPPING,
            SpringClassName.POST_MAPPING,
            SpringClassName.PUT_MAPPING)

    const val REQUEST_HEADER_DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n"

    const val ESCAPE_REQUEST_HEADER_DEFAULT_NONE = "\\n\\t\\t\\n\\t\\t\\n\\uE000\\uE001\\uE002\\n\\t\\t\\t\\t\\n"

}