package com.itangcent.easyapi.core.rule.context

import com.itangcent.easyapi.core.http.HttpRequestWrapper
import com.itangcent.easyapi.core.http.HttpResponseWrapper
import com.itangcent.easyapi.core.http.ScriptHttpClient
import com.itangcent.easyapi.core.logging.IdeaConsole
import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.parser.ScriptConfigWrapper
import com.itangcent.easyapi.core.rule.parser.ScriptFilesWrapper
import com.itangcent.easyapi.core.rule.parser.ScriptHelper
import com.itangcent.easyapi.core.rule.parser.ScriptRuntime
import com.itangcent.easyapi.core.rule.parser.ScriptStorageWrapper
import com.itangcent.easyapi.core.util.RuleToolUtils
import com.itangcent.easyapi.core.util.text.RegexUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.full.valueParameters
import kotlin.reflect.jvm.javaMethod

/**
 * Builds the script-context profile for a [RuleKey] from its self-describing
 * [com.itangcent.easyapi.core.rule.RuleKeyScheme] plus reflection over the
 * runtime script wrappers.
 *
 * This replaces the former `RuleScriptContextCatalog`: the *semantics* (which
 * context kinds, which execution mode, which extra bindings, whether a key is
 * static-config / dry-runnable / JSON-valued) now live on the [RuleKey]
 * itself via its scheme, and this object only turns those semantics into the
 * AI-facing [RuleScriptProfile] — reflecting the public, Groovy-callable
 * methods of the wrapper classes rather than a stale hand-written list.
 *
 * The binding-name → wrapper-class mapping here is pure mechanism (not
 * semantics): it stays in the rule module because the wrappers themselves are
 * rule-module types.
 */
object RuleKeyScriptProfiler {

    private val excludedMethodNames = setOf("equals", "hashCode", "toString", "wait", "notify", "notifyAll", "finalize")

    /** Only members *declared by the plugin's own classes* are listed in the
     *  AI catalog. Standard types (the java.util.List behind `collection`,
     *  kotlin.String in signatures, …) are already known to the LLM — the
     *  catalog exists to teach the plugin-specific script-object surface, so
     *  members inherited from external supertypes are dropped on both
     *  channels. */
    private const val EASY_API_PACKAGE = "com.itangcent"

    /** Builds a complete script-context profile for one registered rule key. */
    fun describe(key: RuleKey<*>, source: String): RuleScriptProfile {
        val definition = definitionFor(key.scheme)

        return RuleScriptProfile(
            key = key.name,
            aliases = key.aliases,
            source = source,
            executionMode = definition.executionMode,
            description = definition.summary,
            bindings = definition.bindings,
            objectRefs = definition.objectRefs,
            notes = definition.notes
        )
    }

    /**
     * Collects the unique [ScriptObjectApi]s referenced by [profile]'s
     * [RuleScriptProfile.objectRefs], deduplicated by id. This is the shared
     * object dictionary the AI tooling joins against: the profile carries only
     * id references, and the caller resolves them via this dictionary.
     */
    fun collectAllObjects(profile: RuleScriptProfile): List<ScriptObjectApi> {
        val allRefs = profile.objectRefs.toSet()
        val result = mutableListOf<ScriptObjectApi>()
        val seen = HashSet<String>()

        // it-context objects (ContextKind.typeClass), keyed by the it binding's
        // objectTypes (context-kind ids).
        profile.bindings.firstOrNull { it.name == "it" }?.objectTypes?.forEach { kindId ->
            val ctxKind = try {
                ContextKind.valueOf(kindId.uppercase())
            } catch (_: IllegalArgumentException) { null }
            if (ctxKind != null && allRefs.contains(kindId) && seen.add(kindId)) {
                result.add(objectApi(kindId, ctxKind.typeClass, ctxKind.description))
            }
        }

        // Common objects
        commonGroovyObjects.forEach { obj ->
            if (allRefs.contains(obj.id) && seen.add(obj.id)) {
                result.add(obj)
            }
        }

        // Additional objects (from additionalBindingSchemes)
        profile.bindings.forEach { binding ->
            val s = additionalBindingSchemes[binding.name]
            if (s?.objectClass != null && allRefs.contains(binding.name) && seen.add(binding.name)) {
                result.add(objectApi(binding.name, s.objectClass, s.objectDescription ?: s.bindingDescription))
            }
        }

        return result
    }

    /**
     * The complete static dictionary of script objects the profiler can
     * describe: every it-context object (one per [ContextKind]), the common
     * helper objects, and every additional binding with a reflected wrapper
     * class.
     *
     * `get_script_object_api` resolves ids against this dictionary. It is
     * derived purely from the code — not from any particular rule key — so
     * every id (class/method/field/parameter/request/…) resolves regardless of
     * which key the model is currently authoring.
     */
    fun allScriptObjects(): List<ScriptObjectApi> {
        val seen = HashSet<String>()
        val result = mutableListOf<ScriptObjectApi>()

        ContextKind.entries.forEach { kind ->
            if (seen.add(kind.id)) result.add(objectApi(kind.id, kind.typeClass, kind.description))
        }
        commonGroovyObjects.forEach { obj ->
            if (seen.add(obj.id)) result.add(obj)
        }
        additionalBindingSchemes.forEach { (id, s) ->
            val cls = s.objectClass ?: return@forEach
            if (seen.add(id)) result.add(objectApi(id, cls, s.objectDescription ?: s.bindingDescription))
        }
        return result
    }

    private fun definitionFor(scheme: com.itangcent.easyapi.core.rule.RuleKeyScheme): ProfileDefinition {
        if (scheme.staticConfiguration) {
            return ProfileDefinition(
                executionMode = "static-configuration",
                summary = scheme.summary ?: "Read as static configuration; the value is not evaluated as a script.",
                bindings = emptyList(),
                objectRefs = emptyList()
            )
        }
        // Every non-static key shares one evaluation path: the engine decides
        // dynamically whether the value is a Groovy rule (groovy: prefix), a
        // literal, or another RuleParser-supported form. There is no per-key
        // execution mode.
        val additionalRefs = scheme.additionalBindings.mapNotNull { binding ->
            // Only script objects (with a reflected wrapper class) go into
            // objectRefs. A context injection (a/b field members) or an opaque
            // binding has no script-object API and is surfaced as a binding only.
            val s = additionalBindingSchemes[binding.kind]
            if (s != null && s.objectClass != null) binding.name else null
        }
        return ProfileDefinition(
            executionMode = "dynamic",
            summary = scheme.summary
                ?: "The value may be written as a literal, a Groovy rule (groovy: prefix), or any RuleParser-supported form; the engine evaluates it by its shape.",
            bindings = listOf(
                itBinding("it", scheme.contextKinds, "Current rule context. Its concrete type depends on this rule key.")
            ) + commonGroovyBindings + scheme.additionalBindings.map(::bindingFor),
            objectRefs = scheme.contextKinds.map(ContextKind::id) + commonObjectIds + additionalRefs,
            notes = scheme.notes
        )
    }

    // ── additional-binding registration (mechanism) ─────────────────
    //
    // The additional binding *names* — and, for context injections, their
    // *injected type* — are declared by the RuleKeyScheme (see
    // `RuleBinding`). This table only supplies mechanism detail that cannot
    // live in a core.rule scheme without dragging in concrete wrapper/core
    // types: the wrapper class to introspect (if any) and the AI-facing
    // descriptions. A script object (with a reflected wrapper class) has a
    // non-null `objectClass` and appears in the profile's `objectRefs`; a
    // binding with `objectClass = null` (e.g. `a`/`b`, which are field members,
    // or an opaque channel-owned model) is surfaced as a binding only.

    private data class AdditionalBindingScheme(
        val objectTypes: List<String>,
        val availability: String,
        val bindingDescription: String,
        /** Wrapper class to introspect for the object API, or `null` when the
         *  binding has no stable core surface (opaque channel-owned model). */
        val objectClass: KClass<*>? = null,
        /** Description for the object API; defaults to [bindingDescription]. */
        val objectDescription: String? = null
    )

    private val additionalBindingSchemes: Map<String, AdditionalBindingScheme> = mapOf(
        "a" to AdditionalBindingScheme(listOf("field", "method"), "always", "Member being compared for ordering."),
        "b" to AdditionalBindingScheme(listOf("field", "method"), "always", "Member being compared for ordering."),
        "api" to AdditionalBindingScheme(listOf("api"), "key-specific", "Script-facing mutable API endpoint.", ScriptApiEndpoint::class, "Script-facing mutable API endpoint."),
        "endpoint" to AdditionalBindingScheme(listOf("endpoint"), "key-specific", "Mutable API endpoint that produced the export item.", ScriptApiEndpoint::class, "Script-facing mutable API endpoint."),
        "request" to AdditionalBindingScheme(listOf("request"), "always for this event", "HTTP request wrapper; header mutations are carried to the send/retry attempt.", HttpRequestWrapper::class, "Script-facing HTTP request wrapper."),
        "response" to AdditionalBindingScheme(listOf("response"), "always for this event", "HTTP response wrapper; discard() requests a bounded retry.", HttpResponseWrapper::class, "Script-facing HTTP response wrapper."),
        // A plain java.util.List at runtime. It stays a *type anchor*: the
        // com.itangcent-only reflection policy lists no JDK methods, and the
        // LLM already knows the standard List API.
        "collection" to AdditionalBindingScheme(listOf("collection"), "always for this event", "Read-only list-like collection of the exported endpoints (a standard List — use its usual API).", List::class),
        "item" to AdditionalBindingScheme(listOf("item"), "always for this event", "Mutable channel-owned export item."),
        "document" to AdditionalBindingScheme(listOf("document"), "always for this event", "Mutable channel-owned document model.")
    )

    private fun bindingFor(binding: com.itangcent.easyapi.core.rule.RuleBinding): ScriptBinding {
        val name = binding.name
        // When the declared kind is a ContextKind id (e.g. "field"/"method"),
        // the binding is a plain context injection — it is NOT a script object,
        // so it has no reflected API and never appears in objectRefs.
        if (binding.kind in com.itangcent.easyapi.core.rule.ContextKind.entries.map { it.id }) {
            return ScriptBinding(
                name,
                objectTypes = listOf(binding.kind),
                availability = "always",
                description = "Injected ${binding.kind} context value; compare/inspect it directly (no script-object API)."
            )
        }
        // Otherwise the binding is a script object (or an opaque key-specific
        // model); `kind` is the object id used to look up the reflected wrapper.
        val s = additionalBindingSchemes[binding.kind]
            ?: return ScriptBinding(name, objectTypes = listOf(binding.kind), availability = "key-specific", description = "Key-specific runtime binding.")
        return ScriptBinding(name, objectTypes = s.objectTypes, availability = s.availability, description = s.bindingDescription)
    }

    private fun itBinding(name: String, kinds: List<ContextKind>, description: String): ScriptBinding =
        ScriptBinding(
            name,
            objectTypes = kinds.map(ContextKind::id),
            availability = "always",
            description = description + discriminatorSuffix(name, kinds)
        )

    private fun discriminatorSuffix(name: String, kinds: List<ContextKind>): String {
        if (kinds.size <= 1) return ""
        val values = kinds.map(ContextKind::id).distinct().joinToString("/") { "'$it'" }
        return " Discriminate with $name.contextType(), which returns $values."
    }

    // ── object reflection (mechanism) ──────────────────────────────

    private fun objectApi(id: String, type: KClass<*>, description: String): ScriptObjectApi {
        // The AI catalog lists only members *declared by the plugin's own
        // classes* (package `com.itangcent`): the LLM already knows standard
        // types, so a wrapper over one (the java.util.List behind `collection`)
        // is carried as a type anchor with an intentionally empty method list
        // rather than a JDK method dump.
        //
        // Enumeration channel follows the declaring type's language — not the
        // caller's:
        //
        //  * Kotlin types (every script wrapper except `collection`) go through
        //    KClass.memberFunctions. It reads @Metadata, so each function
        //    arrives with real parameter names, an accurate visibility, and no
        //    compiler-synthetic $default/access$ bridges.
        //  * Pure Java types (e.g. java.util.List behind `collection`) have no
        //    @Metadata; kotlin-reflect then exposes only an approximate Kotlin
        //    view that drops real members (e.g. size()). The Java reflection
        //    channel stays the faithful view for *Java-declared* com.itangcent
        //    classes — none exist today, so it is hit only to confirm that a
        //    standard external type has no plugin-specific methods to list.
        //
        // Kotlin *properties* are intentionally not listed: script authors work
        // through callables, and the AI catalog mirrors that callable surface.
        val members = if (type.java.isAnnotationPresent(kotlin.Metadata::class.java)) {
            kotlinMemberFunctions(type)
        } else {
            javaMethods(type)
        }
        return ScriptObjectApi(
            id = id,
            type = type.qualifiedName ?: type.simpleName ?: "unknown",
            description = description,
            methods = members
                .distinctBy { "${it.name}(${it.parameters.joinToString { parameter -> parameter.type }})" }
                .sortedWith(
                    compareBy(ScriptMethodApi::name)
                        .thenBy { it.parameters.size }
                        // Deterministic tie-break for same-named overloads:
                        // neither reflection source guarantees an ordering, and
                        // overloads like `notNullOrEmpty(String)` /
                        // `notNullOrEmpty(Object)` share both the name and the
                        // parameter count — without this their relative order
                        // would flip between runs/JDKs.
                        .thenBy { it.parameters.joinToString("\u0000") { parameter -> parameter.type } }
                )
        )
    }

    /** Kotlin-declared callable surface, read via kotlin-reflect. */
    private fun kotlinMemberFunctions(type: KClass<*>): List<ScriptMethodApi> =
        type.memberFunctions
            .asSequence()
            .filter { it.name !in excludedMethodNames }
            // Only what rule scripts (Groovy) can call stays: public members.
            // Kotlin surfaces internal/protected/private as their own
            // visibilities; Java-declared members surface as null and are
            // checked via the backing method instead.
            .filter { function ->
                when (function.visibility) {
                    KVisibility.PUBLIC -> true
                    null -> function.javaMethod?.let { Modifier.isPublic(it.modifiers) } ?: true
                    else -> false
                }
            }
            // @Deprecated members are compatibility shims for historical .rules
            // (the legacy 4-arg ScriptApiEndpoint setters). They remain fully
            // callable at runtime for old rule scripts but must not steer new
            // rules, so they stay out of the AI-facing catalog. Detection is a
            // metadata read (findAnnotation) because kotlinc emits
            // kotlin.Deprecated with BINARY retention — invisible to
            // isAnnotationPresent on the JVM method.
            .filterNot { it.isDeprecatedShim() }
            // Only members declared by the plugin's classes (com.itangcent)
            // are listed. Anything inherited from an external supertype is
            // standard API the LLM already knows — and Java-declared members
            // carry no parameter names here, so listing them would add noise.
            .filter { it.isDeclaredByEasyApi() }
            .map(::functionApi)
            .toList()

    /** Java-declared callable surface, for types without Kotlin @Metadata. */
    private fun javaMethods(type: KClass<*>): List<ScriptMethodApi> =
        type.java.methods
            .asSequence()
            .filter { Modifier.isPublic(it.modifiers) && it.name !in excludedMethodNames }
            // Compiler noise: $default/access$ bridges (Kotlin-only) and the
            // universal Object members.
            .filterNot { it.isSynthetic }
            .filterNot { it.declaringClass == Any::class.java || it.declaringClass == Object::class.java }
            // Java-declared members cannot carry kotlin.Deprecated; probe the
            // JVM annotation directly.
            .filterNot { it.isAnnotationPresent(java.lang.Deprecated::class.java) }
            // Same com.itangcent-only policy as the Kotlin channel: the JDK
            // methods a standard type inherits (Collection/Iterable/Object for
            // java.util.List) are standard API and intentionally unlisted —
            // the object stays a type anchor with an empty method list.
            .filter { it.declaringClass.name.startsWith(EASY_API_PACKAGE) }
            .map(::methodApi)
            .toList()

    private fun functionApi(function: KFunction<*>): ScriptMethodApi {
        // The backing JVM method supplies only what @Metadata cannot:
        // vararg-ness and (for Java-declared members) names.
        val javaMethod = try {
            function.javaMethod
        } catch (_: LinkageError) {
            null // kotlin-reflect absent from the runtime classpath.
        }
        val valueParameters = function.valueParameters
        return ScriptMethodApi(
            name = function.name,
            returns = renderType(function.returnType),
            parameters = valueParameters.mapIndexed { index, parameter ->
                ScriptParameterApi(
                    name = parameter.name ?: javaParameterName(javaMethod, index),
                    type = renderType(parameter.type),
                    vararg = javaMethod?.isVarArgs == true && index == valueParameters.lastIndex
                )
            }
        )
    }

    /**
     * JVM-side rendering for the Java reflection channel (types without
     * Kotlin @Metadata). No nullability is known here, and parameter names
     * come from the `MethodParameters` attribute — absent without
     * `-parameters`, so names fall back to the synthetic `arg0/arg1/...`.
     */
    private fun methodApi(method: Method): ScriptMethodApi = ScriptMethodApi(
        name = method.name,
        returns = renderType(method.genericReturnType),
        parameters = method.genericParameterTypes.mapIndexed { index, type ->
            ScriptParameterApi(
                name = method.parameters[index].name ?: "arg$index",
                type = renderType(type),
                vararg = method.isVarArgs && index == method.parameterCount - 1
            )
        }
    )

    /**
     * JVM-side name fallback for a parameter kotlin-reflect could not name
     * (Java-declared members have no @Metadata). Without `-parameters` the JVM
     * exposes only the synthetic `arg0/arg1/...`.
     */
    private fun javaParameterName(javaMethod: Method?, index: Int): String =
        javaMethod?.parameters?.getOrNull(index)?.name ?: "arg$index"

    /**
     * True when [this] member is declared by one of the plugin's own classes
     * (package `com.itangcent`) — the only members the AI catalog lists.
     * Declaring class is read off the backing JVM method, which resolves to
     * the type that actually declares the function (a wrapper's own class, or
     * the com.itangcent supertype/interface it inherits from). Members with
     * no resolvable backing method are kept — only members we can positively
     * attribute to an external class are dropped.
     */
    private fun KFunction<*>.isDeclaredByEasyApi(): Boolean {
        val declaringClass = try {
            javaMethod?.declaringClass
        } catch (_: LinkageError) {
            null // kotlin-reflect absent from the runtime classpath.
        }
        return declaringClass == null || declaringClass.name.startsWith(EASY_API_PACKAGE)
    }

    /**
     * True when [this] Kotlin function is a `@Deprecated` compatibility shim.
     *
     * kotlinc emits `kotlin.Deprecated` with BINARY retention: it is recorded in
     * the class-file `@Metadata` but not exposed through `isAnnotationPresent`
     * on the backing JVM method. findAnnotation reads metadata annotations
     * regardless of retention, so it is the reliable probe for Kotlin-declared
     * shims; Java-declared members fall back to `java.lang.Deprecated`.
     */
    private fun KFunction<*>.isDeprecatedShim(): Boolean {
        val fromKotlinMetadata = try {
            findAnnotation<Deprecated>() != null
        } catch (_: LinkageError) {
            false // kotlin-reflect absent from the runtime classpath.
        }
        if (fromKotlinMetadata) return true
        val javaMethod = try {
            javaMethod
        } catch (_: LinkageError) {
            null
        }
        return javaMethod?.isAnnotationPresent(java.lang.Deprecated::class.java) == true
    }

    private fun renderType(type: KType): String {
        // KType.toString may render platform types (Java-declared members,
        // e.g. `collection` over java.util.List) with a trailing '!' — strip
        // the marker; script authors never see platform-ness.
        return type.toString()
            .removeSuffix("!")
            .replace("java.lang.", "")
            .replace("java.util.", "")
            .replace("kotlin.collections.", "")
            .replace("kotlin.", "")
    }

    /** Java-reflection counterpart of [renderType] for the Java channel. */
    private fun renderType(type: Type): String = type.typeName
        .replace("java.lang.", "")
        .replace("java.util.", "")
        .replace("kotlin.collections.", "")
        .replace("kotlin.", "")

    // ── common bindings / objects ──────────────────────────────────

    private val commonGroovyBindings = listOf(
        ScriptBinding("logger", aliases = listOf("LOG"), objectTypes = listOf("logger"), availability = "always", description = "EasyAPI console logger."),
        ScriptBinding("session", aliases = listOf("S", "sessionStorage"), objectTypes = listOf("session"), availability = "always", description = "Operation session storage."),
        ScriptBinding("tool", aliases = listOf("T"), objectTypes = listOf("tool"), availability = "always", description = "RuleToolUtils conversion, collection, JSON, string, and date helpers."),
        ScriptBinding("regex", aliases = listOf("RE"), objectTypes = listOf("regex"), availability = "always", description = "Regular-expression helpers."),
        ScriptBinding("files", aliases = listOf("F"), objectTypes = listOf("files"), availability = "always", description = "File save helper."),
        ScriptBinding("config", aliases = listOf("C"), objectTypes = listOf("config"), availability = "always", description = "Resolved EasyAPI configuration values."),
        ScriptBinding("localStorage", objectTypes = listOf("localStorage"), availability = "always", description = "Persistent local storage."),
        ScriptBinding("fieldContext", objectTypes = listOf("fieldContext"), availability = "always", description = "Current object-model field path."),
        ScriptBinding("httpClient", objectTypes = listOf("httpClient"), availability = "when an HTTP client is configured", description = "Blocking HTTP adapter for Groovy rules."),
        ScriptBinding("helper", aliases = listOf("H"), objectTypes = listOf("helper"), availability = "always", description = "PSI class and documentation-link lookup helper."),
        ScriptBinding("runtime", aliases = listOf("R"), objectTypes = listOf("runtime"), availability = "always", description = "Project, module, and source-file metadata.")
    )

    private val commonGroovyObjects = listOf(
        objectApi("logger", IdeaConsole::class, "EasyAPI console logger."),
        objectApi("session", ScriptStorageWrapper::class, "Operation session storage."),
        objectApi("tool", RuleToolUtils::class, "Rule utility methods."),
        objectApi("regex", RegexUtils::class, "Regular-expression utility methods."),
        objectApi("files", ScriptFilesWrapper::class, "File save utility."),
        objectApi("config", ScriptConfigWrapper::class, "Resolved configuration reader."),
        objectApi("localStorage", ScriptStorageWrapper::class, "Persistent local storage."),
        objectApi("fieldContext", ScriptFieldPathContext::class, "Current field path helper."),
        objectApi("httpClient", ScriptHttpClient::class, "Synchronous adapter to the EasyAPI HTTP client."),
        objectApi("helper", ScriptHelper::class, "PSI lookup helper."),
        objectApi("runtime", ScriptRuntime::class, "Project and module metadata helper.")
    )

    /** Ids of [commonGroovyObjects] — the shared helper objects every dynamic key references. */
    private val commonObjectIds get() = commonGroovyObjects.map { it.id }

    private data class ProfileDefinition(
        val executionMode: String,
        val summary: String,
        val bindings: List<ScriptBinding>,
        val objectRefs: List<String>,
        val notes: List<String> = emptyList()
    )
}
