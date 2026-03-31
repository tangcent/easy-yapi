package com.itangcent.easyapi.core.di

import com.itangcent.easyapi.settings.Settings
import kotlin.reflect.KClass

/**
 * Annotation that conditions bean loading on the presence of a class.
 *
 * If the specified class is not available on the classpath, the annotated
 * class will not be loaded by SPI loaders.
 *
 * @param value The fully qualified name of the required class
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnClass(val value: String)

/**
 * Annotation that conditions bean loading on the absence of a class.
 *
 * If the specified class is available on the classpath, the annotated
 * class will not be loaded by SPI loaders.
 *
 * @param value The fully qualified name of the class that must be absent
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnMissingClass(val value: String)

/**
 * Annotation that conditions bean loading on a setting value.
 *
 * If the specified setting does not match the expected value, the annotated
 * class will not be loaded by SPI loaders.
 *
 * @param key The setting key to check
 * @param value The expected value for the setting
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConditionOnSetting(val key: String, val value: String)

/**
 * Annotation that excludes specific classes from being loaded alongside the annotated class.
 *
 * When this annotation is present, the specified classes will be filtered out
 * from SPI loading results.
 *
 * @param value The classes to exclude
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class Exclusion(val value: Array<KClass<*>>)

/**
 * Evaluates conditional annotations on classes to determine if they should be loaded.
 *
 * Supports [ConditionOnClass], [ConditionOnMissingClass], and [ConditionOnSetting] annotations.
 */
object ConditionEvaluator {
    /**
     * Evaluates all conditional annotations on a class.
     *
     * @param clazz The class to evaluate
     * @param settings Optional settings for [ConditionOnSetting] evaluation
     * @return true if all conditions are satisfied, false otherwise
     */
    fun evaluate(clazz: KClass<*>, settings: Settings? = null): Boolean {
        clazz.java.getAnnotation(ConditionOnClass::class.java)?.let {
            if (!isClassAvailable(it.value)) return false
        }
        clazz.java.getAnnotation(ConditionOnMissingClass::class.java)?.let {
            if (isClassAvailable(it.value)) return false
        }
        clazz.java.getAnnotation(ConditionOnSetting::class.java)?.let { annotation ->
            if (settings != null) {
                val actual = getSettingValue(settings, annotation.key)
                if (actual != annotation.value) return false
            }
        }
        return true
    }

    private fun isClassAvailable(fqn: String): Boolean {
        return runCatching { Class.forName(fqn) }.isSuccess
    }

    private fun getSettingValue(settings: Settings, key: String): String? = when (key) {
        "builtInConfig" -> settings.builtInConfig
        "remoteConfig" -> settings.remoteConfig.joinToString("\n")
        "recommendConfig" -> settings.recommendConfigs
        "logLevel" -> settings.logLevel.toString()
        "httpTimeOut" -> settings.httpTimeOut.toString()
        "unsafeSsl" -> settings.unsafeSsl.toString()
        "httpClient" -> settings.httpClient
        "yapiServer" -> settings.yapiServer
        "yapiToken" -> settings.yapiTokens
        "postmanToken" -> settings.postmanToken
        "feignEnable" -> settings.feignEnable.toString()
        "jaxrsEnable" -> settings.jaxrsEnable.toString()
        "actuatorEnable" -> settings.actuatorEnable.toString()
        else -> null
    }
}
