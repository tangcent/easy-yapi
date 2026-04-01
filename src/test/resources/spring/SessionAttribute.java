package org.springframework.web.bind.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class SessionAttribute(
    val value: String = "",
    val name: String = "",
    val required: Boolean = true
)
