package com.itangcent.easyapi.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.settings.state.UnifiedAppSettingsState
import com.itangcent.easyapi.settings.state.UnifiedProjectSettingsState
import com.itangcent.easyapi.util.json.GsonUtils
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure

/**
 * Reflective implementation of [SettingBinder].
 *
 * Reads/writes any [Settings] by reflecting on its `@StorageScope`-annotated
 * properties and routing each to the unified [UnifiedAppSettingsState] (APPLICATION scope)
 * or [UnifiedProjectSettingsState] (PROJECT scope). Values are stored as `String` in the
 * nested map and coerced to/from the property's Kotlin type (R-A-19).
 *
 * ## Caching
 * - **Reflection metadata** ([ModuleSchema]) is computed once per `KClass<*>` and
 *   cached — no per-`read()` reflection cost (CC-5).
 * - **Module values** are cached per-type with a ~10s TTL. `save(T)` invalidates only
 *   `T`'s cache entry.
 *
 * ## Error handling
 * Missing/unrecognized state yields the type's defaults (no crash — R-A-5).
 *
 * Requirements: R-A-17, R-A-18, R-A-19.
 */
@Service(Service.Level.PROJECT)
class DefaultSettingBinder(
    private val project: Project
) : SettingBinder {

    companion object {
        fun getInstance(project: Project): DefaultSettingBinder = project.service()
        private const val CACHE_TTL_MS = 10_000L // 10 seconds
    }

    /** Reflection metadata cache: KClass<*> -> ModuleSchema (computed once). */
    private val schemaCache = ConcurrentHashMap<KClass<*>, ModuleSchema>()

    /** Value cache: KClass<*> -> (instance, expireAt). */
    private val valueCache = ConcurrentHashMap<KClass<*>, ValueEntry<*>>()

    private val appState: UnifiedAppSettingsState get() = UnifiedAppSettingsState.getInstance()
    private val projState: UnifiedProjectSettingsState get() = UnifiedProjectSettingsState.getInstance(project)

    override fun <T : Settings> read(type: KClass<T>): T {
        return tryRead(type)
            ?: error("Cannot construct settings module ${type.qualifiedName}; ensure all constructor parameters have defaults")
    }

    override fun <T : Settings> tryRead(type: KClass<T>): T? {
        @Suppress("UNCHECKED_CAST")
        val cached = valueCache[type] as? ValueEntry<T>
        if (cached != null && !cached.isExpired()) {
            return cached.value
        }
        val value = readFresh(type) ?: return null
        valueCache[type] = ValueEntry(value, System.currentTimeMillis() + CACHE_TTL_MS)
        return value
    }

    override fun <T : Settings> save(settings: T) {
        val type = settings::class
        val schema = getOrCreateSchema(type)
        writeModule(schema, settings)
        // Invalidate cache for this module only (finer-grained than legacy)
        valueCache.remove(type)
        // Fire settings change listener (R-A-8)
        project.messageBus.syncPublisher(SettingsChangeListener.TOPIC).settingsChanged()
    }

    // ---- Internals ----

    @Suppress("UNCHECKED_CAST")
    private fun <T : Settings> readFresh(type: KClass<T>): T? {
        val schema = getOrCreateSchema(type)
        // Create default instance (requires all constructor params to have defaults)
        val instance = schema.createDefaultInstance() ?: return null
        // Fill each annotated field from the matching unified state component
        for (field in schema.fields) {
            val raw = when (field.scope) {
                Scope.APPLICATION -> appState.getValue(schema.moduleKey, field.name)
                Scope.PROJECT -> projState.getValue(schema.moduleKey, field.name)
            }
            if (raw != null) {
                val value = deserialize(raw, field.type)
                if (value != null) {
                    field.set(instance, value)
                }
            }
        }
        return instance as T
    }

    private fun writeModule(schema: ModuleSchema, settings: Settings) {
        // Copy each annotated field from the module to the matching unified state component
        for (field in schema.fields) {
            val value = field.get(settings)
            val serialized = serialize(value, field.type)
            when (field.scope) {
                Scope.APPLICATION -> appState.setValue(schema.moduleKey, field.name, serialized)
                Scope.PROJECT -> projState.setValue(schema.moduleKey, field.name, serialized)
            }
        }
        // Unified state components persist automatically via PersistentStateComponent contract;
        // no explicit loadState call needed.
    }

    // ---- Schema computation (cached) ----

    private fun getOrCreateSchema(type: KClass<*>): ModuleSchema {
        return schemaCache.computeIfAbsent(type) { schemaType ->
            val moduleKey = schemaType.qualifiedName
                ?: error("Settings must have a qualified name: $schemaType")
            val fields = schemaType.memberProperties
                .filterIsInstance<KMutableProperty1<*, *>>()
                .map { prop ->
                    val scopeAnn = prop.findAnnotation<StorageScope>()
                    val scope = scopeAnn?.value ?: Scope.APPLICATION // default APP (R-A-2)
                    @Suppress("UNCHECKED_CAST")
                    FieldInfo(prop.name, scope, prop.returnType, prop as KMutableProperty1<Any, Any?>)
                }
            ModuleSchema(schemaType, moduleKey, fields)
        }
    }

    // ---- Type coercion (R-A-19) ----

    private fun deserialize(raw: String, type: KType): Any? {
        val classifier = type.classifier
        return when {
            classifier == String::class -> raw
            classifier == Boolean::class -> raw.toBooleanStrictOrNull() ?: raw.toBoolean()
            classifier == Int::class -> raw.toIntOrNull()
            isStringArray(type) -> runCatching { GsonUtils.fromJson<Array<String>>(raw) }.getOrNull()
            else -> raw
        }
    }

    private fun serialize(value: Any?, type: KType): String? {
        if (value == null) return null
        val classifier = type.classifier
        return when {
            classifier == String::class -> value as String
            classifier == Boolean::class -> value.toString()
            classifier == Int::class -> value.toString()
            isStringArray(type) -> GsonUtils.toJson(value)
            else -> value.toString()
        }
    }

    private fun isStringArray(type: KType): Boolean {
        val erased = type.jvmErasure
        if (!erased.java.isArray) return false
        return type.arguments.firstOrNull()?.type?.classifier == String::class
    }

    // ---- Schema model ----

    private data class FieldInfo(
        val name: String,
        val scope: Scope,
        val type: KType,
        val setter: KMutableProperty1<Any, Any?>
    ) {
        fun get(target: Any): Any? = setter.get(target)
        fun set(target: Any, value: Any?) = setter.set(target, value)
    }

    private class ModuleSchema(
        val type: KClass<*>,
        val moduleKey: String,
        val fields: List<FieldInfo>
    ) {
        fun createDefaultInstance(): Settings? {
            val constructor = type.primaryConstructor ?: return null
            return try {
                constructor.callBy(emptyMap()) as? Settings
            } catch (e: Exception) {
                null // Cannot construct with defaults; caller returns null
            }
        }
    }

    private class ValueEntry<T>(
        val value: T,
        private val expireAt: Long
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() > expireAt
    }
}
