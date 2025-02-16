package com.itangcent.idea.plugin.api.export.spring

/*
 * This object holds constants for various Spring class and annotation names.
 * It is used throughout the codebase to refer to these classes and annotations
 * in a consistent manner.
 */
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


    //Spring Boot Actuator Annotations
    const val ENDPOINT_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.Endpoint"
    const val WEB_ENDPOINT_ANNOTATION = "org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint"
    const val CONTROLLER_ENDPOINT_ANNOTATION =
        "org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpoint"
    const val REST_CONTROLLER_ENDPOINT_ANNOTATION =
        "org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint"

    const val READ_OPERATION_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.ReadOperation"
    const val WRITE_OPERATION_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.WriteOperation"
    const val DELETE_OPERATION_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.DeleteOperation"
    const val SELECTOR_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.Selector"

    val ENDPOINT_ANNOTATIONS: Set<String> = setOf(
        ENDPOINT_ANNOTATION,
        WEB_ENDPOINT_ANNOTATION,
        CONTROLLER_ENDPOINT_ANNOTATION,
        REST_CONTROLLER_ENDPOINT_ANNOTATION
    )
    val ENDPOINT_OPERATION_ANNOTATIONS: Set<String> = setOf(
        READ_OPERATION_ANNOTATION,
        WRITE_OPERATION_ANNOTATION,
        DELETE_OPERATION_ANNOTATION
    )
}