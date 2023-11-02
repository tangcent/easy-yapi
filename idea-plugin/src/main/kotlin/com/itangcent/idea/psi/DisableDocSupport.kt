package com.itangcent.idea.psi

import com.google.inject.matcher.Matchers
import com.itangcent.common.logger.Log
import com.itangcent.common.utils.toBool
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.DocHelper
import org.aopalliance.intercept.MethodInterceptor
import org.aopalliance.intercept.MethodInvocation

/*
 * The DisableDocSupport object is responsible for binding the EmptyInterceptor to the ActionContextBuilder.
 * It provides a way to disable the plugin from reading documentation.
 */
object DisableDocSupport {
    /*
     * Binds the EmptyInterceptor to the ActionContextBuilder, enabling the plugin to intercept method invocations.
     * @param builder The ActionContextBuilder to bind the interceptor to.
     */
    fun bind(builder: ActionContext.ActionContextBuilder) {
        builder.bindInterceptor(
            Matchers.subclassesOf(DocHelper::class.java),
            Matchers.any(),
            EmptyInterceptor()
        )
    }
}

/*
 * The EmptyInterceptor class is an interceptor used to disable documentation support.
 * Use 'doc.source.disable' configuration property to determine if documentation is enabled or disabled.
 */
class EmptyInterceptor : MethodInterceptor {

    companion object : Log()

    private val disableDoc by lazy {
        val disable = ActionContext.getContext()
            ?.instance(ConfigReader::class)
            ?.first("doc.source.disable")
            ?.toBool(false) ?: false
        if (disable) {
            LOG.info("disable doc")
        }
        disable
    }

    override fun invoke(invocation: MethodInvocation): Any? {
        if (disableDoc) {
            val returnType = invocation.method.returnType
            return when (returnType) {
                Map::class.java -> emptyMap<String, String?>()
                List::class.java -> emptyList<String>()
                String::class.java -> ""
                Boolean::class.java, Boolean::class.javaObjectType -> false
                else -> null
            }
        }
        return invocation.proceed()
    }
}