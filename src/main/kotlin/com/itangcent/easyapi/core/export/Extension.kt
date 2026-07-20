package com.itangcent.easyapi.core.export

/**
 * A pluggable key-value carrier attached to model objects
 * ([ApiEndpoint], [ApiParameter], [com.itangcent.easyapi.core.psi.model.FieldModel]).
 *
 * The shared model classes carry only protocol-agnostic fields that every
 * channel needs. Feature-specific or channel-specific attributes (e.g. display
 * labels, ordering hints, channel-specific flags) are stored in this map and
 * accessed via Kotlin extension properties defined in per-feature files. This
 * keeps the shared model classes minimal and free of any single channel's
 * concerns, while letting each channel attach the data it needs without
 * subclassing (the model classes are final `data class`es).
 *
 * Pattern (per-feature file, in the feature's own package):
 *
 * ```kotlin
 * val ApiEndpoint.displayLabel: String
 *     get() = extensions["displayLabel"] as? String ?: ""
 *
 * fun ApiEndpoint.setDisplayLabel(value: String) {
 *     extensions["displayLabel"] = value
 * }
 * ```
 *
 * `data class copy()` preserves the [Extension] instance by reference, so
 * copied models retain any previously-attached attributes.
 *
 * When the owning object is mutable and attributes may be attached after
 * construction (e.g. [com.itangcent.easyapi.core.settings.Settings]), declare the
 * field as a [MutableExtension] so writes through [asMutable] always land on
 * the live object:
 *
 * ```kotlin
 * data class Settings(var extensions: MutableExtension = MutableExtension())
 * ```
 */
interface Extension {
    /**
     * The extra key-value pairs attached to the owning model object.
     * The returned map is unmodifiable; use [asMutable] (or construct a
     * [MutableExtension]) to add entries.
     */
    val exts: Map<String, Any?>

    companion object {
        /**
         * An empty, immutable [Extension] — the default for value objects whose
         * attributes are set once at construction and never mutated.
         *
         * Implemented as a distinct singleton rather than a [MutableExtension]
         * so there is no mutable backing store to leak: [asMutable] on it
         * returns a fresh copy, and writes to that copy cannot pollute the
         * shared default. Mutability-coupled containers (e.g. [Settings])
         * should default to `MutableExtension()` instead.
         */
        val EMPTY: Extension = EmptyExtension

        /**
         * Returns a mutable [MutableExtension] for writing.
         *
         * - If the receiver is a [MutableExtension], the returned instance **is**
         *   the receiver; writes are immediately visible on the owning object.
         * - Otherwise (e.g. [EMPTY], or any read-only [Extension]) a **new**
         *   [MutableExtension] copy is returned. Writes to that copy do **not**
         *   affect the original object — the caller must take the returned
         *   reference and install it back onto the owner if persistence is
         *   required. This is why mutable containers should declare their field
         *   as [MutableExtension] rather than [Extension].
         */
        fun Extension.asMutable(): MutableExtension =
            if (this is MutableExtension) this
            else MutableExtension().apply { putAll(exts) }
    }
}

/**
 * Read-only, empty [Extension] singleton backing [Extension.EMPTY].
 *
 * Exposes an empty unmodifiable map; [Extension.asMutable] on it yields a
 * fresh mutable copy, so the shared default can never be polluted.
 */
private object EmptyExtension : Extension {
    private val EMPTY_MAP: Map<String, Any?> = java.util.Collections.emptyMap()
    override val exts: Map<String, Any?>
        get() = EMPTY_MAP
}

/**
 * A mutable [Extension] backed by a [mutableMapOf].
 *
 * Construct model objects with a `MutableExtension()` (or use the model's
 * setter extension properties, which obtain a mutable view) when you need to
 * attach attributes after construction.
 */
class MutableExtension : Extension {
    override val exts: MutableMap<String, Any?> = mutableMapOf()

    /** Returns the value for [key], or `null` if absent. */
    operator fun get(key: String): Any? = exts[key]

    /** Adds or overwrites a single extension key-value pair. */
    operator fun set(key: String, value: Any?) {
        exts[key] = value
    }

    /** Adds all entries from [entries] into the extension map, overwriting existing keys. */
    fun putAll(entries: Map<String, Any?>) {
        exts.putAll(entries)
    }
}
