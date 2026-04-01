package org.springframework.web.bind.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class CookieValue(
    val value: String = "",
    val name: String = "",
    val required: Boolean = true,
    val defaultValue: String = ""
)
