package com.itangcent.easyapi.channel.yapi.model

/**
 * Interface for extending YAPI model objects with custom key-value pairs.
 *
 * Secondary developers who customize YAPI can implement this interface to
 * inject additional fields into the YAPI save API request body. The extra
 * key-value pairs returned by [getExts] are merged into the top-level
 * request map (for [YapiApiDoc]) or into the item-level map (for sub-objects
 * like [YapiHeader], [YapiQuery], etc.).
 */
interface Extension {
    /**
     * Returns extra key-value pairs to be included in the YAPI API request.
     * Keys that collide with standard YAPI fields will overwrite the defaults.
     */
    fun getExts(): Map<String, Any?>
}

/**
 * Contract for YAPI API documentation.
 *
 * Used as the type throughout the export pipeline ([YapiApiClient],
 * [YapiFormatter], [UpdateConfirmation], etc.).
 *
 * The standard implementation is [MutableYapiApiDoc], which is also the only
 * implementation used internally.  It is returned as [YapiApiDoc] so that
 * consumers are decoupled from mutability; only the rule-engine layer casts
 * to [MutableYapiApiDoc] when it needs to modify the document.
 */
interface YapiApiDoc : Extension {
    val title: String
    val path: String
    val method: String
    val desc: String?
    val markdown: String?
    val status: String?
    val tag: List<String>?
    val reqHeaders: List<YapiHeader>?
    val reqQuery: List<YapiQuery>?
    val reqParams: List<YapiPathParam>?
    val reqBodyForm: List<YapiFormParam>?
    val reqBodyOther: String?
    val reqBodyType: String?
    val reqBodyIsJsonSchema: Boolean
    val resBody: String?
    val resBodyType: String?
    val resBodyIsJsonSchema: Boolean
    val tags: List<String>?
    val open: Boolean?

    /**
     * Creates a copy of this [YapiApiDoc] with optionally modified field values.
     *
     * Extension key-value pairs are included via [exts], defaulting to [getExts].
     * Pass an explicit value (e.g. `exts = emptyMap()`) to drop extensions on copy.
     *
     * @return A new [MutableYapiApiDoc] with the requested field values.
     */
    fun copy(
        title: String = this.title,
        path: String = this.path,
        method: String = this.method,
        desc: String? = this.desc,
        markdown: String? = this.markdown,
        status: String? = this.status,
        tag: List<String>? = this.tag,
        reqHeaders: List<YapiHeader>? = this.reqHeaders,
        reqQuery: List<YapiQuery>? = this.reqQuery,
        reqParams: List<YapiPathParam>? = this.reqParams,
        reqBodyForm: List<YapiFormParam>? = this.reqBodyForm,
        reqBodyOther: String? = this.reqBodyOther,
        reqBodyType: String? = this.reqBodyType,
        reqBodyIsJsonSchema: Boolean = this.reqBodyIsJsonSchema,
        resBody: String? = this.resBody,
        resBodyType: String? = this.resBodyType,
        resBodyIsJsonSchema: Boolean = this.resBodyIsJsonSchema,
        tags: List<String>? = this.tags,
        open: Boolean? = this.open,
        exts: Map<String, Any?> = this.getExts(),
    ): MutableYapiApiDoc
}

/**
 * Mutable implementation of [YapiApiDoc].
 *
 * This is the only implementation used in the codebase.  It is always
 * returned as [YapiApiDoc] from public APIs so that callers do not depend
 * on mutability.  The rule-engine layer casts to [MutableYapiApiDoc] when
 * it needs to modify the document (e.g. in `YAPI_SAVE_BEFORE` scripts).
 *
 * All properties are `var` and the extension map is mutable via [setExt].
 * Sub-object lists use mutable types ([MutableYapiHeader], [MutableYapiQuery],
 * [MutableYapiPathParam], [MutableYapiFormParam]) so that individual items
 * can be mutated directly in rule scripts.
 *
 * Create an instance via the companion factory methods.
 */
data class MutableYapiApiDoc(
    override var title: String,
    override var path: String,
    override var method: String,
    override var desc: String? = null,
    override var markdown: String? = null,
    override var status: String? = null,
    override var tag: List<String>? = null,
    override var reqHeaders: List<MutableYapiHeader>? = null,
    override var reqQuery: List<MutableYapiQuery>? = null,
    override var reqParams: List<MutableYapiPathParam>? = null,
    override var reqBodyForm: List<MutableYapiFormParam>? = null,
    override var reqBodyOther: String? = null,
    override var reqBodyType: String? = null,
    override var reqBodyIsJsonSchema: Boolean = false,
    override var resBody: String? = null,
    override var resBodyType: String? = "json",
    override var resBodyIsJsonSchema: Boolean = true,
    override var tags: List<String>? = null,
    override var open: Boolean? = null,
) : YapiApiDoc {

    private val _exts: MutableMap<String, Any?> = mutableMapOf()

    override fun getExts(): Map<String, Any?> = java.util.Collections.unmodifiableMap(_exts)

    /** Adds or overwrites a single extension key-value pair. */
    fun setExt(key: String, value: Any?) {
        _exts[key] = value
    }

    /** Adds all entries from [exts] into the extension map, overwriting existing keys. */
    fun putAllExts(exts: Map<String, Any?>) {
        _exts.putAll(exts)
    }

    @Suppress("UNCHECKED_CAST")
    override fun copy(
        title: String,
        path: String,
        method: String,
        desc: String?,
        markdown: String?,
        status: String?,
        tag: List<String>?,
        reqHeaders: List<YapiHeader>?,
        reqQuery: List<YapiQuery>?,
        reqParams: List<YapiPathParam>?,
        reqBodyForm: List<YapiFormParam>?,
        reqBodyOther: String?,
        reqBodyType: String?,
        reqBodyIsJsonSchema: Boolean,
        resBody: String?,
        resBodyType: String?,
        resBodyIsJsonSchema: Boolean,
        tags: List<String>?,
        open: Boolean?,
        exts: Map<String, Any?>,
    ): MutableYapiApiDoc = MutableYapiApiDoc(
        title = title,
        path = path,
        method = method,
        desc = desc,
        markdown = markdown,
        status = status,
        tag = tag,
        reqHeaders = reqHeaders as? List<MutableYapiHeader>,
        reqQuery = reqQuery as? List<MutableYapiQuery>,
        reqParams = reqParams as? List<MutableYapiPathParam>,
        reqBodyForm = reqBodyForm as? List<MutableYapiFormParam>,
        reqBodyOther = reqBodyOther,
        reqBodyType = reqBodyType,
        reqBodyIsJsonSchema = reqBodyIsJsonSchema,
        resBody = resBody,
        resBodyType = resBodyType,
        resBodyIsJsonSchema = resBodyIsJsonSchema,
        tags = tags,
        open = open,
    ).also { it.putAllExts(exts) }

    companion object {
        /** Creates a [MutableYapiApiDoc] with all fields set to their defaults. */
        @Suppress("UNCHECKED_CAST")
        fun create(
            title: String,
            path: String,
            method: String,
            desc: String? = null,
            markdown: String? = null,
            status: String? = null,
            tag: List<String>? = null,
            reqHeaders: List<YapiHeader>? = null,
            reqQuery: List<YapiQuery>? = null,
            reqParams: List<YapiPathParam>? = null,
            reqBodyForm: List<YapiFormParam>? = null,
            reqBodyOther: String? = null,
            reqBodyType: String? = null,
            reqBodyIsJsonSchema: Boolean = false,
            resBody: String? = null,
            resBodyType: String? = "json",
            resBodyIsJsonSchema: Boolean = true,
            tags: List<String>? = null,
            open: Boolean? = null,
            exts: Map<String, Any?> = emptyMap()
        ): MutableYapiApiDoc = MutableYapiApiDoc(
            title = title,
            path = path,
            method = method,
            desc = desc,
            markdown = markdown,
            status = status,
            tag = tag,
            reqHeaders = reqHeaders as? List<MutableYapiHeader>,
            reqQuery = reqQuery as? List<MutableYapiQuery>,
            reqParams = reqParams as? List<MutableYapiPathParam>,
            reqBodyForm = reqBodyForm as? List<MutableYapiFormParam>,
            reqBodyOther = reqBodyOther,
            reqBodyType = reqBodyType,
            reqBodyIsJsonSchema = reqBodyIsJsonSchema,
            resBody = resBody,
            resBodyType = resBodyType,
            resBodyIsJsonSchema = resBodyIsJsonSchema,
            tags = tags,
            open = open,
        ).also { it.putAllExts(exts) }

        /** Creates a [MutableYapiApiDoc] copied from [source]. */
        @Suppress("UNCHECKED_CAST")
        fun from(source: YapiApiDoc): MutableYapiApiDoc = MutableYapiApiDoc(
            title = source.title,
            path = source.path,
            method = source.method,
            desc = source.desc,
            markdown = source.markdown,
            status = source.status,
            tag = source.tag,
            reqHeaders = source.reqHeaders as? List<MutableYapiHeader>,
            reqQuery = source.reqQuery as? List<MutableYapiQuery>,
            reqParams = source.reqParams as? List<MutableYapiPathParam>,
            reqBodyForm = source.reqBodyForm as? List<MutableYapiFormParam>,
            reqBodyOther = source.reqBodyOther,
            reqBodyType = source.reqBodyType,
            reqBodyIsJsonSchema = source.reqBodyIsJsonSchema,
            resBody = source.resBody,
            resBodyType = source.resBodyType,
            resBodyIsJsonSchema = source.resBodyIsJsonSchema,
            tags = source.tags,
            open = source.open,
        ).also { it.putAllExts(source.getExts()) }
    }
}

// ---------------------------------------------------------------------------
// Sub-model interfaces & mutable implementations
// ---------------------------------------------------------------------------

/**
 * Represents an HTTP header in YAPI API documentation.
 *
 * The mutable implementation is [MutableYapiHeader].
 */
interface YapiHeader : Extension {
    val name: String
    val value: String?
    val desc: String?
    val example: String?
    val required: Int
}

/**
 * Mutable implementation of [YapiHeader].
 *
 * All properties are `var` so they can be modified in rule scripts.
 * The auto-generated data-class [copy] returns a [MutableYapiHeader].
 * Use [putAllExts] after copy if extensions must be preserved.
 */
data class MutableYapiHeader(
    override var name: String,
    override var value: String? = null,
    override var desc: String? = null,
    override var example: String? = null,
    override var required: Int = 0,
) : YapiHeader {

    private val _exts: MutableMap<String, Any?> = mutableMapOf()

    override fun getExts(): Map<String, Any?> = java.util.Collections.unmodifiableMap(_exts)

    /** Adds or overwrites a single extension key-value pair. */
    fun setExt(key: String, value: Any?) {
        _exts[key] = value
    }

    /** Adds all entries from [exts] into the extension map, overwriting existing keys. */
    fun putAllExts(exts: Map<String, Any?>) {
        _exts.putAll(exts)
    }

    companion object {
        /** Creates a [MutableYapiHeader] copied from [source] including extensions. */
        fun from(source: YapiHeader): MutableYapiHeader = MutableYapiHeader(
            name = source.name,
            value = source.value,
            desc = source.desc,
            example = source.example,
            required = source.required,
        ).also { it.putAllExts(source.getExts()) }
    }
}

/**
 * Represents a query parameter in YAPI API documentation.
 *
 * The mutable implementation is [MutableYapiQuery].
 */
interface YapiQuery : Extension {
    val name: String
    val value: String?
    val desc: String?
    val example: String?
    val required: Int
}

/**
 * Mutable implementation of [YapiQuery].
 *
 * All properties are `var` so they can be modified in rule scripts.
 * The auto-generated data-class [copy] returns a [MutableYapiQuery].
 * Use [putAllExts] after copy if extensions must be preserved.
 */
data class MutableYapiQuery(
    override var name: String,
    override var value: String? = null,
    override var desc: String? = null,
    override var example: String? = null,
    override var required: Int = 0,
) : YapiQuery {

    private val _exts: MutableMap<String, Any?> = mutableMapOf()

    override fun getExts(): Map<String, Any?> = java.util.Collections.unmodifiableMap(_exts)

    /** Adds or overwrites a single extension key-value pair. */
    fun setExt(key: String, value: Any?) {
        _exts[key] = value
    }

    /** Adds all entries from [exts] into the extension map, overwriting existing keys. */
    fun putAllExts(exts: Map<String, Any?>) {
        _exts.putAll(exts)
    }

    companion object {
        /** Creates a [MutableYapiQuery] copied from [source] including extensions. */
        fun from(source: YapiQuery): MutableYapiQuery = MutableYapiQuery(
            name = source.name,
            value = source.value,
            desc = source.desc,
            example = source.example,
            required = source.required,
        ).also { it.putAllExts(source.getExts()) }
    }
}

/**
 * Represents a path parameter in YAPI API documentation.
 *
 * The mutable implementation is [MutableYapiPathParam].
 */
interface YapiPathParam : Extension {
    val name: String
    val example: String?
    val desc: String?
}

/**
 * Mutable implementation of [YapiPathParam].
 *
 * All properties are `var` so they can be modified in rule scripts.
 * The auto-generated data-class [copy] returns a [MutableYapiPathParam].
 * Use [putAllExts] after copy if extensions must be preserved.
 */
data class MutableYapiPathParam(
    override var name: String,
    override var example: String? = null,
    override var desc: String? = null,
) : YapiPathParam {

    private val _exts: MutableMap<String, Any?> = mutableMapOf()

    override fun getExts(): Map<String, Any?> = java.util.Collections.unmodifiableMap(_exts)

    /** Adds or overwrites a single extension key-value pair. */
    fun setExt(key: String, value: Any?) {
        _exts[key] = value
    }

    /** Adds all entries from [exts] into the extension map, overwriting existing keys. */
    fun putAllExts(exts: Map<String, Any?>) {
        _exts.putAll(exts)
    }

    companion object {
        /** Creates a [MutableYapiPathParam] copied from [source] including extensions. */
        fun from(source: YapiPathParam): MutableYapiPathParam = MutableYapiPathParam(
            name = source.name,
            example = source.example,
            desc = source.desc,
        ).also { it.putAllExts(source.getExts()) }
    }
}

/**
 * Represents a form parameter in YAPI API documentation.
 *
 * The mutable implementation is [MutableYapiFormParam].
 */
interface YapiFormParam : Extension {
    val name: String
    val example: String?
    val type: String
    val required: Int
    val desc: String?
}

/**
 * Mutable implementation of [YapiFormParam].
 *
 * All properties are `var` so they can be modified in rule scripts.
 * The auto-generated data-class [copy] returns a [MutableYapiFormParam].
 * Use [putAllExts] after copy if extensions must be preserved.
 */
data class MutableYapiFormParam(
    override var name: String,
    override var example: String? = null,
    override var type: String = "text",
    override var required: Int = 0,
    override var desc: String? = null,
) : YapiFormParam {

    private val _exts: MutableMap<String, Any?> = mutableMapOf()

    override fun getExts(): Map<String, Any?> = java.util.Collections.unmodifiableMap(_exts)

    /** Adds or overwrites a single extension key-value pair. */
    fun setExt(key: String, value: Any?) {
        _exts[key] = value
    }

    /** Adds all entries from [exts] into the extension map, overwriting existing keys. */
    fun putAllExts(exts: Map<String, Any?>) {
        _exts.putAll(exts)
    }

    companion object {
        /** Creates a [MutableYapiFormParam] copied from [source] including extensions. */
        fun from(source: YapiFormParam): MutableYapiFormParam = MutableYapiFormParam(
            name = source.name,
            example = source.example,
            type = source.type,
            required = source.required,
            desc = source.desc,
        ).also { it.putAllExts(source.getExts()) }
    }
}

/**
 * Represents a YAPI category (cart) for organizing APIs.
 * 
 * @property id The cart ID
 * @property name The cart name
 */
data class YapiCart(val id: Long, val name: String)

/**
 * Generic response wrapper for all YAPI API calls.
 *
 * @param T The type of the data payload on success
 * @property data The response data, present on success
 * @property error Error message, present on failure
 */
data class YapiResponse<T>(val data: T? = null, val error: String? = null) {
    val isSuccess: Boolean get() = error == null
    fun getOrNull(): T? = data
    fun errorMessage(): String? = error

    companion object {
        fun <T> success(data: T) = YapiResponse(data = data)
        fun <T> failure(error: String) = YapiResponse<T>(error = error)
    }
}
