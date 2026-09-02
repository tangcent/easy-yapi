package com.itangcent.easyapi.core.rule.context

import com.itangcent.easyapi.core.http.HttpRequestWrapper
import com.itangcent.easyapi.core.http.HttpResponseWrapper
import com.itangcent.easyapi.core.http.ScriptHttpClient
import com.itangcent.easyapi.core.logging.IdeaConsole
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.parser.ScriptConfigWrapper
import com.itangcent.easyapi.core.rule.parser.ScriptFilesWrapper
import com.itangcent.easyapi.core.rule.parser.ScriptHelper
import com.itangcent.easyapi.core.rule.parser.ScriptRuntime
import com.itangcent.easyapi.core.rule.parser.ScriptStorageWrapper
import com.itangcent.easyapi.core.script.pm.PmAuthConfig
import com.itangcent.easyapi.core.script.pm.PmCookies
import com.itangcent.easyapi.core.script.pm.PmExpectation
import com.itangcent.easyapi.core.script.pm.PmHeaderList
import com.itangcent.easyapi.core.script.pm.PmInfo
import com.itangcent.easyapi.core.script.pm.PmObject
import com.itangcent.easyapi.core.script.pm.PmPropertyList
import com.itangcent.easyapi.core.script.pm.PmRequest
import com.itangcent.easyapi.core.script.pm.PmRequestBody
import com.itangcent.easyapi.core.script.pm.PmResponse
import com.itangcent.easyapi.core.script.pm.PmResponseBDD
import com.itangcent.easyapi.core.script.pm.PmResponseBDDNegated
import com.itangcent.easyapi.core.script.pm.PmResponseHave
import com.itangcent.easyapi.core.script.pm.PmResponseHaveNot
import com.itangcent.easyapi.core.script.pm.PmSendRequest
import com.itangcent.easyapi.core.script.pm.PmTest
import com.itangcent.easyapi.core.script.pm.PmVariableScope
import com.itangcent.easyapi.core.util.RuleToolUtils
import com.itangcent.easyapi.core.util.text.RegexUtils
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * Key-aware catalog of rule-script contexts exposed to the AI authoring tools.
 *
 * The catalog is deliberately adjacent to the runtime context wrappers instead
 * of adding metadata to [RuleKey]: keys are also contributed by channels and
 * frameworks, while their execution context is determined by the caller. This
 * keeps the normal RuleKey SPI compact and lets the catalog report an explicit
 * `runtime-specific` fallback for third-party keys it does not yet recognize.
 *
 * Public method/property lists are reflected from the wrapper classes so the
 * AI sees the APIs actually callable by Groovy rather than a duplicated,
 * stale hand-maintained list. Semantic constraints (stage, mutability, and
 * availability) remain explicit metadata because reflection cannot infer them.
 */
object RuleScriptContextCatalog {

    private val excludedMethodNames = setOf("equals", "hashCode", "toString", "wait", "notify", "notifyAll", "finalize")

    /** Builds a complete script-context profile for one registered rule key. */
    fun describe(key: RuleKey<*>, source: String): RuleScriptProfile =
        profile(key.name, key.aliases, source)

    /**
     * Returns a conservative profile for a key name when only the name is
     * known. AI tools should prefer [describe] after resolving the key through
     * RuleKeyRegistry so aliases and the contributing source are included.
     */
    fun describe(keyName: String): RuleScriptProfile = profile(keyName, emptyList(), "unknown")

    /**
     * Context kinds a dry-run evaluation of [keyName]'s rule value should run
     * against (see [com.itangcent.easyapi.core.rule.RuleDryRunValidator]),
     * as `ItKind` id strings (`"class"`, `"method"`, `"field"`,
     * `"parameter"`, `"empty"`).
     *
     * Returns an empty list when the key's evaluation stage cannot be
     * faithfully reproduced with the standard bindings alone:
     * - static-configuration keys are never evaluated as scripts;
     * - keys with extra bindings (`api`, `request`/`response`, `collection`,
     *   `item`, `document`, `a`/`b`) would fail spuriously without them;
     * - `type`-context keys (`json.rule.convert`) need a resolved type no
     *   representative element can supply.
     *
     * Keys whose rule-evaluation stage has only the common bindings keep the
     * [itKindsFor] mapping (including two-stage Postman/Hoppscotch script
     * keys — the first stage is a plain Groovy rule).
     */
    fun dryRunContextKinds(keyName: String): List<String> = when {
        keyName in staticConfigurationKeys -> emptyList()
        keyName in postmanCollectionKeys || keyName in hoppscotchCollectionKeys -> emptyList()
        keyName.endsWith(".format.after") -> emptyList()
        keyName == "http.call.before" || keyName == "http.call.after" -> emptyList()
        keyName == "export.after" || keyName.startsWith("custom.export.") -> emptyList()
        keyName == "field.order.with" -> emptyList()
        else -> itKindsFor(keyName).map(ItKind::id).filter { it != ItKind.TYPE.id }
    }

    private fun profile(key: String, aliases: List<String>, source: String): RuleScriptProfile {
        val definition = when {
            key in staticConfigurationKeys -> ProfileDefinition(
                executionMode = "static-configuration",
                summary = "Read as static configuration; EasyAPI does not evaluate a Groovy script for this key.",
                stages = emptyList()
            )

            key in postmanPreRequestKeys -> postmanScriptDefinition(key, preRequest = true)
            key in postmanTestKeys -> postmanScriptDefinition(key, preRequest = false)
            key in postmanCollectionKeys -> collectionEventDefinition(
                key = key,
                collectionElementType = "PostmanEndpointContext",
                description = "Collection export event. It evaluates as a Groovy rule; it does not run as a dashboard pm script."
            )

            key == "postman.format.after" -> formatAfterDefinition(
                key = key,
                itemType = "PostmanItem",
                itemDescription = "Mutable Postman export item. This channel model is intentionally opaque to core; inspect its properties in the channel documentation before relying on it."
            )

            key in hoppscotchScriptKeys -> groovyDefinition(
                itKinds = if (key.contains("class.")) listOf(ItKind.CLASS) else listOf(ItKind.METHOD, ItKind.CLASS),
                summary = "Generates a Hoppscotch script string. EasyAPI evaluates this rule with Groovy context; the generated script is executed by Hoppscotch, not by EasyAPI."
            )

            key in hoppscotchCollectionKeys -> collectionEventDefinition(
                key = key,
                collectionElementType = "ApiEndpoint",
                description = "Collection export event. It evaluates as a Groovy rule and exposes exported endpoints; it is not a Hoppscotch runtime script context."
            )

            key == "hopp.format.after" -> groovyDefinition(
                itKinds = listOf(ItKind.METHOD),
                additionalBindings = listOf(apiBinding("endpoint", "Endpoint being formatted; mutations write through to the export model.")),
                additionalObjects = listOf(apiObject("endpoint")),
                summary = "Runs after one Hoppscotch endpoint is formatted."
            )

            key == "openapi.format.after" -> groovyDefinition(
                itKinds = listOf(ItKind.METHOD),
                additionalBindings = listOf(
                    ScriptBinding(
                        name = "document",
                        objectTypes = listOf("document"),
                        availability = "always for this event",
                        description = "Mutable OpenAPI document before serialization. It is a channel-owned model, so this core catalog does not claim a stable method surface."
                    )
                ),
                additionalObjects = listOf(opaqueObject("document", "OpenApiDocument", "Mutable channel-owned OpenAPI model.")),
                summary = "Runs after the OpenAPI document is built and before it is serialized."
            )

            key in openApiDocumentKeys -> groovyDefinition(
                itKinds = listOf(ItKind.EMPTY),
                summary = "Document-level OpenAPI metadata rule, evaluated once per export without a PSI element. Use helper/H for project-wide PSI lookups."
            )

            key == "http.call.before" -> groovyDefinition(
                itKinds = listOf(ItKind.EMPTY),
                additionalBindings = listOf(httpRequestBinding()),
                additionalObjects = listOf(httpRequestObject()),
                summary = "Runs immediately before an HTTP request is sent; request headers can be mutated."
            )

            key == "http.call.after" -> groovyDefinition(
                itKinds = listOf(ItKind.EMPTY),
                additionalBindings = listOf(httpRequestBinding(), httpResponseBinding()),
                additionalObjects = listOf(httpRequestObject(), httpResponseObject()),
                summary = "Runs after an HTTP response; call response.discard() to request a bounded retry."
            )

            key in exportAfterKeys -> groovyDefinition(
                itKinds = listOf(ItKind.METHOD, ItKind.CLASS, ItKind.EMPTY),
                additionalBindings = listOf(apiBinding("api", "Mutable API endpoint being exported; mutations write through to the export model.")),
                additionalObjects = listOf(apiObject("api")),
                summary = "Runs after an API endpoint is built; api exposes export-model mutations and cURL rendering."
            )

            key == "field.order.with" -> groovyDefinition(
                itKinds = listOf(ItKind.FIELD, ItKind.METHOD),
                additionalBindings = listOf(
                    itBinding("a", listOf(ItKind.FIELD, ItKind.METHOD), "First member being compared for ordering."),
                    itBinding("b", listOf(ItKind.FIELD, ItKind.METHOD), "Second member being compared for ordering.")
                ),
                additionalObjects = listOf(
                    objectApi("a-field", ScriptPsiFieldContext::class, "Field member for ordering comparison."),
                    objectApi("a-method", ScriptPsiMethodContext::class, "Getter/setter member for ordering comparison.")
                ),
                summary = "Compares two object-model members; a and b can be field or method contexts."
            )

            key.startsWith("custom.class.parse.") -> groovyDefinition(listOf(ItKind.CLASS))
            key.startsWith("custom.method.parse.") -> groovyDefinition(listOf(ItKind.METHOD))
            key.startsWith("custom.export.") -> groovyDefinition(
                itKinds = listOf(ItKind.METHOD, ItKind.CLASS, ItKind.EMPTY),
                additionalBindings = listOf(apiBinding("api", "Mutable API endpoint being exported.")),
                additionalObjects = listOf(apiObject("api"))
            )

            else -> groovyDefinition(itKindsFor(key))
        }

        return RuleScriptProfile(
            key = key,
            aliases = aliases,
            source = source,
            executionMode = definition.executionMode,
            summary = RuleScriptContextSummary(
                executionMode = definition.executionMode,
                itContexts = definition.stages.flatMap { stage ->
                    stage.bindings.firstOrNull { it.name == "it" }?.objectTypes.orEmpty()
                }.distinct(),
                bindings = definition.stages.flatMap { it -> it.bindings.map(ScriptBinding::name) }.distinct(),
                detailTool = "get_rule_context"
            ),
            description = definition.summary,
            stages = definition.stages,
            notes = definition.notes
        )
    }

    private fun postmanScriptDefinition(key: String, preRequest: Boolean): ProfileDefinition {
        val itKinds = if (key.contains("class.")) listOf(ItKind.CLASS) else listOf(ItKind.METHOD, ItKind.CLASS)
        val phase = if (preRequest) "pre-request" else "post-response"
        return ProfileDefinition(
            executionMode = "postman-$phase",
            summary = "This key has two stages: EasyAPI first evaluates the rule value with Groovy/it, then the resulting text runs as a Postman-compatible $phase script.",
            stages = listOf(
                groovyStage(
                    name = "rule-evaluation",
                    itKinds = itKinds,
                    description = "Evaluates the rule value while exporting. This stage has EasyAPI Groovy bindings, including it."
                ),
                postmanStage(preRequest)
            ),
            notes = listOf(
                "Do not use EasyAPI it/helper/session bindings in the generated pm script.",
                if (preRequest) "response is null before the request is sent." else "response is available only after the request completes."
            )
        )
    }

    private fun collectionEventDefinition(
        key: String,
        collectionElementType: String,
        description: String
    ): ProfileDefinition = ProfileDefinition(
        executionMode = "groovy-rule",
        summary = description,
        stages = listOf(
            groovyStage(
                name = "rule-evaluation",
                itKinds = listOf(ItKind.EMPTY),
                description = description,
                additionalBindings = listOf(
                    ScriptBinding(
                        name = "collection",
                        objectTypes = listOf("collection"),
                        availability = "always for this event",
                        description = "Collection passed by $key; element type: $collectionElementType."
                    )
                ),
                additionalObjects = listOf(collectionObject(collectionElementType))
            )
        )
    )

    private fun formatAfterDefinition(key: String, itemType: String, itemDescription: String): ProfileDefinition = ProfileDefinition(
        executionMode = "groovy-rule",
        summary = "Runs after a $itemType is created; item and endpoint are key-specific export extensions.",
        stages = listOf(
            groovyStage(
                name = "rule-evaluation",
                itKinds = listOf(ItKind.METHOD),
                description = "Runs after formatting one exported item for $key.",
                additionalBindings = listOf(
                    ScriptBinding("item", objectTypes = listOf("item"), availability = "always for this event", description = itemDescription),
                    apiBinding("endpoint", "Mutable API endpoint that produced the export item.")
                ),
                additionalObjects = listOf(opaqueObject("item", itemType, itemDescription), apiObject("endpoint"))
            )
        )
    )

    private fun groovyDefinition(
        itKinds: List<ItKind>,
        additionalBindings: List<ScriptBinding> = emptyList(),
        additionalObjects: List<ScriptObjectApi> = emptyList(),
        summary: String? = null
    ): ProfileDefinition = ProfileDefinition(
        executionMode = "groovy-rule",
        summary = summary ?: "EasyAPI evaluates this key as a Groovy rule with the shown PSI context and common helpers.",
        stages = listOf(groovyStage("rule-evaluation", itKinds, additionalBindings = additionalBindings, additionalObjects = additionalObjects))
    )

    private fun groovyStage(
        name: String,
        itKinds: List<ItKind>,
        description: String = "EasyAPI Groovy rule evaluation.",
        additionalBindings: List<ScriptBinding> = emptyList(),
        additionalObjects: List<ScriptObjectApi> = emptyList()
    ): RuleScriptStage {
        val itBinding = itBinding("it", itKinds, "Current rule context. Its concrete type depends on this rule key.")
        val itObjects = itKinds.map { kind -> objectApi(kind.id, kind.type, kind.description) }
        return RuleScriptStage(
            name = name,
            executionMode = "groovy-rule",
            description = description,
            bindings = listOf(itBinding) + commonGroovyBindings + additionalBindings,
            objects = (itObjects + commonGroovyObjects + additionalObjects).distinctBy(ScriptObjectApi::id)
        )
    }

    private fun postmanStage(preRequest: Boolean): RuleScriptStage {
        val phase = if (preRequest) "postman-prerequest" else "postman-test"
        val bindings = mutableListOf(
            ScriptBinding("pm", objectTypes = listOf("pm"), availability = "always", description = "Postman-compatible root object."),
            ScriptBinding("environment", objectTypes = listOf("environment"), availability = "always", description = "Active environment variables."),
            ScriptBinding("globals", objectTypes = listOf("globals"), availability = "always", description = "Global variables."),
            ScriptBinding("collectionVariables", objectTypes = listOf("collectionVariables"), availability = "always", description = "Project/collection variables."),
            ScriptBinding("request", objectTypes = listOf("request"), availability = if (preRequest) "mutable before send" else "completed request; treat as read-only", description = "Current HTTP request."),
            ScriptBinding("test", objectTypes = listOf("test"), availability = "always", description = "Registers named test assertions."),
            ScriptBinding("cookies", objectTypes = listOf("cookies"), availability = if (preRequest) "empty before response" else "response cookies", description = "Cookies for this request/response cycle."),
            ScriptBinding("info", objectTypes = listOf("info"), availability = "always", description = "Script execution metadata."),
            ScriptBinding("logger", objectTypes = listOf("logger"), availability = "always", description = "EasyAPI console logger.")
        )
        if (!preRequest) {
            bindings.add(ScriptBinding("response", objectTypes = listOf("response"), availability = "always", description = "Received HTTP response."))
        }
        return RuleScriptStage(
            name = "generated-script",
            executionMode = phase,
            description = "The string returned by rule evaluation runs here. It has pm-style bindings and no EasyAPI it/helper/session bindings.",
            bindings = bindings,
            objects = postmanObjects
        )
    }

    private fun itBinding(name: String, kinds: List<ItKind>, description: String): ScriptBinding =
        ScriptBinding(
            name,
            objectTypes = kinds.map(ItKind::id),
            availability = "always",
            description = description + discriminatorSuffix(name, kinds)
        )

    /**
     * Hint appended to a binding description when the binding can hold more
     * than one context kind. The contract requires the script to tell the
     * kinds apart, and the built-in discriminator is `contextType()`. Without
     * the hint the model invents Groovy MOP idioms such as
     * `it.respondsTo('containingClass')` to guess the kind (issue #756).
     */
    private fun discriminatorSuffix(name: String, kinds: List<ItKind>): String {
        if (kinds.size <= 1) return ""
        val values = kinds.map(ItKind::contextType).distinct().joinToString("/") { "'$it'" }
        return " Discriminate with $name.contextType(), which returns $values."
    }

    private fun apiBinding(name: String, description: String): ScriptBinding =
        ScriptBinding(name, objectTypes = listOf(name), availability = "key-specific", description = description)

    private fun httpRequestBinding(): ScriptBinding =
        ScriptBinding("request", objectTypes = listOf("request"), availability = "always for this event", description = "HTTP request wrapper; header mutations are carried to the send/retry attempt.")

    private fun httpResponseBinding(): ScriptBinding =
        ScriptBinding("response", objectTypes = listOf("response"), availability = "always for this event", description = "HTTP response wrapper; discard() requests a bounded retry.")

    private fun apiObject(id: String): ScriptObjectApi =
        objectApi(id, ScriptApiEndpoint::class, "Script-facing mutable API endpoint.")

    private fun httpRequestObject(): ScriptObjectApi =
        objectApi("request", HttpRequestWrapper::class, "Script-facing HTTP request wrapper.")

    private fun httpResponseObject(): ScriptObjectApi =
        objectApi("response", HttpResponseWrapper::class, "Script-facing HTTP response wrapper.")

    private fun collectionObject(elementType: String): ScriptObjectApi =
        objectApi("collection", List::class, "Read-only list-like collection; element type: $elementType.")
            .copy(type = "kotlin.collections.List<$elementType>")

    private fun opaqueObject(id: String, type: String, description: String): ScriptObjectApi =
        ScriptObjectApi(id = id, type = type, description = description, properties = emptyList(), methods = emptyList())

    private fun objectApi(id: String, type: KClass<*>, description: String): ScriptObjectApi {
        val methods = type.java.methods
            .asSequence()
            .filter { Modifier.isPublic(it.modifiers) && it.name !in excludedMethodNames }
            .filterNot { it.declaringClass == Any::class.java || it.declaringClass == Object::class.java }
            .toList()
        return ScriptObjectApi(
            id = id,
            type = type.qualifiedName ?: type.simpleName ?: "unknown",
            description = description,
            properties = propertiesOf(methods),
            methods = methods
                .asSequence()
                .filterNot(::isBeanAccessor)
                .map(::methodApi)
                .distinctBy { "${it.name}(${it.parameters.joinToString { parameter -> parameter.type }})" }
                .sortedWith(compareBy(ScriptMethodApi::name).thenBy { it.parameters.size })
                .toList()
        )
    }

    private fun propertiesOf(methods: List<Method>): List<ScriptPropertyApi> {
        val setters = methods.filter { it.name.startsWith("set") && it.parameterCount == 1 }
            .associateBy { it.name.removePrefix("set").replaceFirstChar(Char::lowercase) }
        return methods.asSequence()
            .filter { method ->
                method.parameterCount == 0 &&
                    ((method.name.startsWith("get") && method.name.length > 3) ||
                        (method.name.startsWith("is") && method.name.length > 2))
            }
            .map { getter ->
                val name = if (getter.name.startsWith("is")) {
                    getter.name.removePrefix("is").replaceFirstChar(Char::lowercase)
                } else {
                    getter.name.removePrefix("get").replaceFirstChar(Char::lowercase)
                }
                ScriptPropertyApi(name, renderType(getter.genericReturnType), name in setters)
            }
            .distinctBy(ScriptPropertyApi::name)
            .sortedBy(ScriptPropertyApi::name)
            .toList()
    }

    private fun isBeanAccessor(method: Method): Boolean =
        (method.parameterCount == 0 &&
            ((method.name.startsWith("get") && method.name.length > 3) ||
                (method.name.startsWith("is") && method.name.length > 2))) ||
            (method.name.startsWith("set") && method.name.length > 3 && method.parameterCount == 1)

    private fun methodApi(method: Method): ScriptMethodApi = ScriptMethodApi(
        name = method.name,
        returns = renderType(method.genericReturnType),
        parameters = method.genericParameterTypes.mapIndexed { index, type ->
            ScriptParameterApi(
                name = method.parameters[index].name ?: "arg$index",
                type = renderType(type),
                optional = false,
                vararg = method.isVarArgs && index == method.parameterCount - 1
            )
        }
    )

    private fun renderType(type: Type): String = type.typeName
        .replace("java.lang.", "")
        .replace("java.util.", "")
        .replace("kotlin.collections.", "")
        .replace("kotlin.", "")

    private fun itKindsFor(key: String): List<ItKind> = when {
        key == "ignore" -> listOf(ItKind.CLASS, ItKind.METHOD, ItKind.FIELD, ItKind.PARAMETER)
        key == "api.name" || key == "folder.name" -> listOf(ItKind.METHOD, ItKind.CLASS)
        key.startsWith("method.") || key == "endpoint.prefix.path" || key == "path.multi" -> listOf(ItKind.METHOD)
        key.startsWith("class.") -> listOf(ItKind.CLASS)
        key.startsWith("param.") || key.startsWith("custom.param.") -> listOf(ItKind.PARAMETER)
        key.startsWith("field.") -> listOf(ItKind.FIELD, ItKind.METHOD)
        key == "json.rule.convert" -> listOf(ItKind.TYPE)
        key.startsWith("json.field.") -> listOf(ItKind.FIELD, ItKind.METHOD)
        key.startsWith("json.class.") -> listOf(ItKind.CLASS)
        key == "json.additional.field" || key == "json.unwrapped" -> listOf(ItKind.FIELD, ItKind.METHOD)
        key.startsWith("api.class.") -> listOf(ItKind.CLASS)
        key.startsWith("api.method.") -> listOf(ItKind.METHOD)
        key.startsWith("api.param.") -> listOf(ItKind.PARAMETER)
        key.startsWith("custom.class.") -> listOf(ItKind.CLASS)
        key.startsWith("custom.method.") || key == "custom.http.method" || key == "custom.path" -> listOf(ItKind.METHOD, ItKind.CLASS)
        key.startsWith("enum.") -> listOf(ItKind.CLASS)
        key == "constant.field.ignore" -> listOf(ItKind.FIELD)
        key == "properties.prefix" -> listOf(ItKind.CLASS)
        key.endsWith(".host") -> listOf(ItKind.CLASS, ItKind.EMPTY)
        else -> listOf(ItKind.EMPTY)
    }

    private data class ProfileDefinition(
        val executionMode: String,
        val summary: String,
        val stages: List<RuleScriptStage>,
        val notes: List<String> = emptyList()
    )

    /**
     * The runtime `contextType()` return value for the kind, as defined by
     * the [ScriptItContext] hierarchy — used to teach the discriminator.
     */
    private enum class ItKind(val id: String, val contextType: String, val type: KClass<*>, val description: String) {
        EMPTY("empty", "unknown", ScriptItContext::class, "No PSI element is supplied; common helper bindings remain available."),
        CLASS("class", "class", ScriptPsiClassContext::class, "PSI class context."),
        METHOD("method", "method", ScriptPsiMethodContext::class, "PSI method context."),
        FIELD("field", "field", ScriptPsiFieldContext::class, "PSI field context; object-model fields may also be represented by a method context."),
        PARAMETER("parameter", "param", ScriptPsiParameterContext::class, "PSI parameter context."),
        TYPE("type", "class", ScriptPsiTypeContext::class, "PSI type context without a source element.")
    }

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
        ScriptBinding("runtime", aliases = listOf("R"), objectTypes = listOf("runtime"), availability = "always", description = "Project, module, and source-file metadata."),
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
        objectApi("runtime", ScriptRuntime::class, "Project and module metadata helper."),
    )

    private val postmanObjects = listOf(
        objectApi("pm", PmObject::class, "Postman-compatible root object."),
        objectApi("environment", PmVariableScope::class, "Active environment variables."),
        objectApi("globals", PmVariableScope::class, "Global variables."),
        objectApi("collectionVariables", PmVariableScope::class, "Project/collection variables."),
        objectApi("request", PmRequest::class, "Current HTTP request."),
        objectApi("response", PmResponse::class, "Received HTTP response."),
        objectApi("test", PmTest::class, "Named test registration helper."),
        objectApi("cookies", PmCookies::class, "Response cookie helper."),
        objectApi("info", PmInfo::class, "Script execution metadata."),
        objectApi("logger", IdeaConsole::class, "EasyAPI console logger."),
        objectApi("headers", PmHeaderList::class, "Mutable HTTP header list."),
        objectApi("body", PmRequestBody::class, "HTTP request body."),
        objectApi("propertyList", PmPropertyList::class, "Form or URL-encoded property list."),
        objectApi("auth", PmAuthConfig::class, "Request authentication settings."),
        objectApi("sendRequest", PmSendRequest::class, "Additional HTTP request sender."),
        objectApi("expectation", PmExpectation::class, "Fluent assertion builder."),
        objectApi("responseBdd", PmResponseBDD::class, "Positive response BDD assertions."),
        objectApi("responseBddNot", PmResponseBDDNegated::class, "Negated response BDD assertions."),
        objectApi("responseHave", PmResponseHave::class, "Response body/header assertion methods."),
        objectApi("responseHaveNot", PmResponseHaveNot::class, "Negated response body/header assertion methods."),
    )

    private val staticConfigurationKeys = setOf(
        "markdown.template", "markdown.template.language", "markdown.template.url.ttl.seconds",
        "markdown.template.url.max.bytes", "max.deep", "max.elements"
    )

    private val postmanPreRequestKeys = setOf("postman.prerequest", "postman.class.prerequest")
    private val postmanTestKeys = setOf("postman.test", "postman.class.test")
    private val postmanCollectionKeys = setOf("postman.collection.prerequest", "postman.collection.test")
    private val hoppscotchScriptKeys = setOf("hopp.prerequest", "hopp.class.prerequest", "hopp.test", "hopp.class.test")
    private val hoppscotchCollectionKeys = setOf("hopp.collection.prerequest", "hopp.collection.test")
    private val openApiDocumentKeys = setOf("openapi.host", "openapi.server.url", "openapi.info.title", "openapi.info.version", "openapi.info.description")
    private val exportAfterKeys = setOf("export.after", "custom.export.after")
}

/** A key-specific context contract returned to the AI as structured JSON. */
data class RuleScriptProfile(
    val key: String,
    val aliases: List<String>,
    val source: String,
    val executionMode: String,
    val summary: RuleScriptContextSummary,
    val description: String,
    val stages: List<RuleScriptStage>,
    val notes: List<String>
)

/** Compact metadata embedded in `list_rule_keys` results. */
data class RuleScriptContextSummary(
    val executionMode: String,
    val itContexts: List<String>,
    val bindings: List<String>,
    val detailTool: String
)

/** One runtime stage for a rule value or the emitted script it produces. */
data class RuleScriptStage(
    val name: String,
    val executionMode: String,
    val description: String,
    val bindings: List<ScriptBinding>,
    val objects: List<ScriptObjectApi>
)

/** A script variable and the object API identifiers it can reference. */
data class ScriptBinding(
    val name: String,
    val aliases: List<String> = emptyList(),
    val objectTypes: List<String>,
    val availability: String,
    val description: String
)

/** Public, Groovy-callable API for a script object. */
data class ScriptObjectApi(
    val id: String,
    val type: String,
    val description: String,
    val properties: List<ScriptPropertyApi>,
    val methods: List<ScriptMethodApi>
)

/** A readable script property; [writable] describes whether Groovy can assign it. */
data class ScriptPropertyApi(
    val name: String,
    val type: String,
    val writable: Boolean
)

/** A callable script method and its runtime-visible signature. */
data class ScriptMethodApi(
    val name: String,
    val returns: String,
    val parameters: List<ScriptParameterApi>
)

/** One method parameter in [ScriptMethodApi]. */
data class ScriptParameterApi(
    val name: String,
    val type: String,
    val optional: Boolean,
    val vararg: Boolean
)
