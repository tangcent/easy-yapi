package com.itangcent.intellij.context

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.spi.SetupAble
import com.itangcent.intellij.extend.guice.FieldHandler
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.utils.ActionKeys
import java.lang.reflect.Field

/**
 * Supports automatic clearing of fields marked with @AutoClear annotation.
 * This class initializes the auto-clearing functionality by registering a field handler
 * that processes fields annotated with @AutoClear.
 */
class AutoClearSupporter : SetupAble {
    override fun init() {
        // Register the AutoClearFieldHandler to handle fields annotated with @AutoClear
        ActionContext.addDefaultInject {
            it.bindFieldHandler(AutoClear::class.java, AutoClearFieldHandler())
        }
    }
}

/**
 * Field handler that processes fields annotated with @AutoClear.
 * When a field is injected, this handler registers the field with AutoClearExecutor
 * for automatic clearing when the action is completed.
 */
class AutoClearFieldHandler : FieldHandler<Any?, Annotation> {

    override fun afterInjection(injectee: Any?, annotation: Annotation, field: Field) {
        if (injectee == null) return

        val actionContext = ActionContext.getContext()!!
        val autoClearExecutor = actionContext.instance(AutoClearExecutor::class)

        // Register the field for automatic clearing
        autoClearExecutor.registerField(injectee, field)
    }
}

/**
 * Singleton executor responsible for managing and clearing annotated fields.
 * This class keeps track of all fields that need to be cleared and handles
 * the clearing process when an action is completed.
 */
@Singleton
class AutoClearExecutor {

    companion object : Log()

    @Inject
    private lateinit var actionContext: ActionContext

    // List of registered fields that need to be cleared
    private val registeredFields = mutableListOf<Pair<Any, Field>>()

    /**
     * Registers a field for automatic clearing.
     * @param component The object containing the field
     * @param field The field to be cleared
     */
    fun registerField(component: Any, field: Field) {
        registeredFields.add(component to field)
    }

    /**
     * Initializes the executor by setting up a listener for action completion.
     * When an action is completed, all registered fields will be cleared.
     */
    @PostConstruct
    fun init() {
        actionContext.on(ActionKeys.ACTION_COMPLETED) {
            clearAnnotatedProperties()
        }
    }

    /**
     * Clears all registered fields by setting them to their default values.
     * - Collections and Maps are cleared using their clear() method
     * - Primitive types are set to their default values (0, false, etc.)
     * - Objects are set to null
     * Any exceptions during clearing are silently caught and ignored.
     */
    private fun clearAnnotatedProperties() {
        // Clear Java fields
        registeredFields.forEach { (component, field) ->
            field.isAccessible = true
            try {
                when (val value = field.get(component)) {
                    is MutableCollection<*> -> value.clear()
                    is MutableMap<*, *> -> value.clear()
                    else -> {
                        val type = field.type
                        val defaultValue = when (type) {
                            String::class.java -> ""
                            Int::class.java, Integer::class.java -> 0
                            Long::class.java, java.lang.Long::class.java -> 0L
                            Double::class.java, java.lang.Double::class.java -> 0.0
                            Float::class.java, java.lang.Float::class.java -> 0.0f
                            Boolean::class.java, java.lang.Boolean::class.java -> false
                            else -> null
                        }
                        field.set(component, defaultValue)
                    }
                }
            } catch (e: Exception) {
                // Skip if we can't clear or set default value
                LOG.traceError("Failed to clear field ${field.name}", e)
            }
        }
    }
}