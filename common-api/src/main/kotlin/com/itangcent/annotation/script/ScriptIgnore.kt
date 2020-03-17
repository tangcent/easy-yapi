package com.itangcent.annotation.script

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ScriptIgnore(vararg val name: String = [])