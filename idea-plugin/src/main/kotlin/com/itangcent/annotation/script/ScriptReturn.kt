package com.itangcent.annotation.script

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ScriptReturn(val name: String)