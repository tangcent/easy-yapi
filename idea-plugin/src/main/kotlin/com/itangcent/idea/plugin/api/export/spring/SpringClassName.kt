package com.itangcent.idea.plugin.api.export.spring


object SpringClassName {

    val SPRING_REQUEST_RESPONSE: Array<String> = arrayOf(
        "javax.servlet.http.HttpServletRequest",
        "javax.servlet.http.HttpServletResponse"
    )

    var SPRING_CONTROLLER_ANNOTATION: Set<String> =
        mutableSetOf(
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController"
        )

    //file
    const val MULTI_PART_FILE = "org.springframework.web.multipart.MultipartFile"

    //annotations
    const val REQUEST_MAPPING_ANNOTATION = "org.springframework.web.bind.annotation.RequestMapping"
    const val REQUEST_BODY_ANNOTATION = "org.springframework.web.bind.annotation.RequestBody"
    const val REQUEST_PARAM_ANNOTATION = "org.springframework.web.bind.annotation.RequestParam"
    const val MODEL_ATTRIBUTE_ANNOTATION = "org.springframework.web.bind.annotation.ModelAttribute"
    const val PATH_VARIABLE_ANNOTATION = "org.springframework.web.bind.annotation.PathVariable"
    const val COOKIE_VALUE_ANNOTATION = "org.springframework.web.bind.annotation.CookieValue"

    const val REQUEST_HEADER = "org.springframework.web.bind.annotation.RequestHeader"

    const val GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping"
    const val POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping"
    const val PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping"
    const val DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping"
    const val PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping"

    val SPRING_SINGLE_REQUEST_MAPPING_ANNOTATIONS: Set<String> = setOf(
        GET_MAPPING,
        DELETE_MAPPING,
        PATCH_MAPPING,
        POST_MAPPING,
        PUT_MAPPING
    )

    val SPRING_REQUEST_MAPPING_ANNOTATIONS: Set<String> = setOf(
        REQUEST_MAPPING_ANNOTATION,
        GET_MAPPING,
        DELETE_MAPPING,
        PATCH_MAPPING,
        POST_MAPPING,
        PUT_MAPPING
    )

    const val REQUEST_HEADER_DEFAULT_NONE = "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n"

    const val ESCAPE_REQUEST_HEADER_DEFAULT_NONE = "\\n\\t\\t\\n\\t\\t\\n\\uE000\\uE001\\uE002\\n\\t\\t\\t\\t\\n"
}