package com.itangcent.idea.plugin.api.export.quarkus

object QuarkusClassName {
    const val PATH_ANNOTATION = "javax.ws.rs.Path";

    const val DEFAULT_VALUE_ANNOTATION = "javax.ws.rs.DefaultValue";

    //XxxParam
    const val BEAN_PARAM_ANNOTATION = "javax.ws.rs.BeanParam";
    const val COOKIE_PARAM_ANNOTATION = "javax.ws.rs.CookieParam";
    const val FORM_PARAM_ANNOTATION = "javax.ws.rs.FormParam";
    const val HEADER_PARAM_ANNOTATION = "javax.ws.rs.HeaderParam";
    const val MATRIX_PARAM_ANNOTATION = "javax.ws.rs.MatrixParam";
    const val PATH_PARAM_ANNOTATION = "javax.ws.rs.PathParam";
    const val QUERY_PARAM_ANNOTATION = "javax.ws.rs.QueryParam";

    const val HTTP_METHOD_ANNOTATION = "javax.ws.rs.HttpMethod";
    const val POST_ANNOTATION = "javax.ws.rs.POST";
    const val GET_ANNOTATION = "javax.ws.rs.GET";
    const val PUT_ANNOTATION = "javax.ws.rs.PUT";
    const val DELETE_ANNOTATION = "javax.ws.rs.DELETE";
    const val PATCH_ANNOTATION = "javax.ws.rs.PATCH";
    const val HEAD_ANNOTATION = "javax.ws.rs.HEAD";
    const val OPTIONS_ANNOTATION = "javax.ws.rs.OPTIONS";

    val QUARKUS_SINGLE_MAPPING_ANNOTATIONS: List<String> = listOf(
        POST_ANNOTATION,
        GET_ANNOTATION,
        PUT_ANNOTATION,
        DELETE_ANNOTATION,
        PATCH_ANNOTATION,
        HEAD_ANNOTATION,
        OPTIONS_ANNOTATION,
    )

    val QUARKUS_MAPPING_ANNOTATIONS: List<String> = listOf(
        HTTP_METHOD_ANNOTATION,
        POST_ANNOTATION,
        GET_ANNOTATION,
        PUT_ANNOTATION,
        DELETE_ANNOTATION,
        PATCH_ANNOTATION,
        HEAD_ANNOTATION,
        OPTIONS_ANNOTATION,
    )
}