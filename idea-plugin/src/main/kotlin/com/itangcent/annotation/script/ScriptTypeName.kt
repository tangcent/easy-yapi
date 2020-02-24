package com.itangcent.annotation.script

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ScriptTypeName(val name: String)