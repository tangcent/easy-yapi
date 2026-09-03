# EasyApi Rule Script-Context (get_rule_context mirror)

Auto-generated from each rule key's runtime script-context (`RuleContextExporter`). Do **not** edit by hand — run `./gradlew syncRuleContexts`. Fetch one key with `scripts/get_key_context.sh <key>`.

This is the same per-key bindings + script-object API the built-in `get_rule_context` returns: it tells you exactly which script objects (it/request/response/api/…) and methods are callable for a given rule key. After choosing a value format, read the key's section here before authoring a Groovy/Postman script.

Format: a `## <key>` section declares that key's bindings and the object `id`s it makes callable. Every object's full method signatures live ONCE under `Script-Object API Reference` below — look up the ids listed in a key's section there (or just run `scripts/get_key_context.sh <key>`, which joins them for you).

## Script-Object API Reference

### object: class

Type: `com.itangcent.easyapi.core.rule.context.ClassContext` — PSI class context.

- `ann(name: String): String?`
- `ann(name: String, attr: String): String?`
- `annMap(name: String): Map<String, Any?>?`
- `annMaps(name: String): List<Map<String, Any?>>?`
- `annValue(name: String): Any?`
- `annValue(name: String, attr: String): Any?`
- `canonicalText(): String`
- `contextType(): String`
- `defineCode(): String?`
- `doc(): String?`
- `doc(tag: String): String?`
- `doc(tag: String, subTag: String): String?`
- `docs(tag: String): List<String>?`
- `extends(): Array<com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext>?`
- `fieldCnt(): Int`
- `fields(): Array<com.itangcent.easyapi.core.rule.context.ScriptPsiFieldContext>`
- `getExt(name: String): Any?`
- `getName(): String?`
- `hasAnn(name: String): Boolean`
- `hasDoc(tag: String): Boolean`
- `hasModifier(modifier: String): Boolean`
- `implements(): Array<com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext>?`
- `isAnnotationType(): Boolean`
- `isArray(): Boolean`
- `isCollection(): Boolean`
- `isEnum(): Boolean`
- `isExtend(superClass: String): Boolean`
- `isInnerClass(): Boolean`
- `isInterface(): Boolean`
- `isMap(): Boolean`
- `isNormalType(): Boolean`
- `isPackagePrivate(): Boolean`
- `isPrimitive(): Boolean`
- `isPrimitiveWrapper(): Boolean`
- `isPrivate(): Boolean`
- `isProtected(): Boolean`
- `isPublic(): Boolean`
- `isStatic(): Boolean`
- `methodCnt(): Int`
- `methods(): Array<com.itangcent.easyapi.core.rule.context.ScriptPsiMethodContext>`
- `modifiers(): List<String>`
- `name(): String`
- `outerClass(): com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext?`
- `packageName(): String?`
- `psi(): com.intellij.psi.PsiElement?`
- `qualifiedName(): String?`
- `setExt(name: String, value: Any?): Unit`
- `sourceCode(): String?`
- `superClass(): com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext?`
- `toJson(): String`
- `toJson5(): String`
- `type(): com.itangcent.easyapi.core.rule.context.ScriptTypeContext`

### object: logger

Type: `com.itangcent.easyapi.core.logging.IdeaConsole` — EasyAPI console logger.

- `debug(msg: String): Unit`
- `error(msg: String, t: Throwable?): Unit`
- `info(msg: String): Unit`
- `print(msg: String): Unit`
- `println(msg: String): Unit`
- `trace(msg: String): Unit`
- `warn(msg: String, t: Throwable?): Unit`

### object: session

Type: `com.itangcent.easyapi.core.rule.parser.ScriptStorageWrapper` — Operation session storage.

- `clear(): Unit`
- `clear(group: String?): Unit`
- `get(name: String?): Any?`
- `get(group: String?, name: String?): Any?`
- `keys(): Array<Any?>`
- `keys(group: String?): Array<Any?>`
- `peek(name: String?): Any?`
- `peek(group: String?, name: String?): Any?`
- `pop(name: String?): Any?`
- `pop(group: String?, name: String?): Any?`
- `push(name: String?, value: Any?): Unit`
- `push(group: String?, name: String?, value: Any?): Unit`
- `remove(name: String): Unit`
- `remove(group: String?, name: String): Unit`
- `set(name: String?, value: Any?): Unit`
- `set(group: String?, name: String?, value: Any?): Unit`

### object: tool

Type: `com.itangcent.easyapi.core.util.RuleToolUtils` — Rule utility methods.

- `anyIntersect(any: Any?, other: Any?): Boolean`
- `asArray(any: Any?): Array<*>?`
- `asList(any: Any?): List<*>?`
- `camel2Underline(str: String?): String?`
- `capitalize(str: String?): String?`
- `copy2Clipboard(str: String): Unit`
- `debug(any: Any?): String`
- `equalOrIntersect(any: Any?, other: Any?): Boolean`
- `format(time: Long, pattern: String?): String?`
- `headLine(str: String?): String?`
- `intersect(any: Any?, other: Any?): Array<*>?`
- `isAlpha(str: String?): Boolean`
- `isNullOrEmpty(any: Any?): Boolean`
- `isNumeric(str: String?): Boolean`
- `lowerCase(str: String?): String?`
- `newList(items: Array<out Any>): List<*>`
- `newMap(): Map<*, *>`
- `newSet(items: Array<out Any>): Set<*>`
- `notNullOrBlank(str: String?): Boolean`
- `notNullOrEmpty(any: Any?): Boolean`
- `notNullOrEmpty(str: String?): Boolean`
- `now(): String`
- `now(pattern: String?): String`
- `nullOrBlank(str: String?): Boolean`
- `nullOrEmpty(str: String?): Boolean`
- `parseJson(json: String?): Any?`
- `prettyJson(obj: Any?): String?`
- `removePrefix(str: String?, prefix: String?): String?`
- `removeSuffix(str: String?, suffix: String?): String?`
- `repeat(str: String?, repeat: Int): String?`
- `repeat(str: String?, separator: String, repeat: Int): String?`
- `reverse(str: String?): String?`
- `split(str: String?): Array<String>?`
- `split(str: String?, separatorChars: String?): Array<String>?`
- `substringAfter(str: String?, separator: String?): String?`
- `substringAfterLast(str: String?, separator: String?): String?`
- `substringBefore(str: String?, separator: String?): String?`
- `substringBeforeLast(str: String?, separator: String?): String?`
- `substringBetween(str: String?, tag: String?): String?`
- `substringBetween(str: String?, open: String?, close: String?): String?`
- `substringsBetween(str: String?, open: String?, close: String?): Array<String>?`
- `swapCase(str: String?): String?`
- `toCamelCase(str: String?, capitalizeFirstLetter: Boolean, delimiters: CharArray): String?`
- `toJson(obj: Any?): String?`
- `today(): String`
- `uncapitalize(str: String?): String?`
- `upperCase(str: String?): String?`

### object: regex

Type: `com.itangcent.easyapi.core.util.text.RegexUtils` — Regular-expression utility methods.

- `contains(regex: String?, content: String?): Boolean`
- `count(regex: String?, content: String?): Int`
- `delAll(regex: String, content: String): String`
- `delBefore(regex: String, content: String): String?`
- `delFirst(regex: String, content: String): String`
- `escape(content: String?): String?`
- `extract(regex: String?, content: String?, template: String?): String?`
- `findAll(regex: String, content: String, group: Int): List<String>`
- `findAllGroup0(regex: String, content: String): List<String>?`
- `findAllGroup1(regex: String, content: String): List<String>?`
- `get(regex: String?, content: String?, groupIndex: Int): String?`
- `getAllGroups(regex: String?, content: String?): List<String>?`
- `getGroup0(regex: String, content: String): String?`
- `getGroup1(regex: String, content: String): String?`
- `isMatch(regex: String?, content: String?): Boolean`
- `replaceAll(content: String, regex: String, replacementTemplate: String): String?`

### object: files

Type: `com.itangcent.easyapi.core.rule.parser.ScriptFilesWrapper` — File save utility.

- `save(content: String, path: String): Unit`
- `save(content: String, charset: String, path: String): Unit`

### object: config

Type: `com.itangcent.easyapi.core.rule.parser.ScriptConfigWrapper` — Resolved configuration reader.

- `get(name: String): String?`
- `getValues(name: String): List<String>`
- `resolveProperty(property: String): String`

### object: localStorage

Type: `com.itangcent.easyapi.core.rule.parser.ScriptStorageWrapper` — Persistent local storage.

- `clear(): Unit`
- `clear(group: String?): Unit`
- `get(name: String?): Any?`
- `get(group: String?, name: String?): Any?`
- `keys(): Array<Any?>`
- `keys(group: String?): Array<Any?>`
- `peek(name: String?): Any?`
- `peek(group: String?, name: String?): Any?`
- `pop(name: String?): Any?`
- `pop(group: String?, name: String?): Any?`
- `push(name: String?, value: Any?): Unit`
- `push(group: String?, name: String?, value: Any?): Unit`
- `remove(name: String): Unit`
- `remove(group: String?, name: String): Unit`
- `set(name: String?, value: Any?): Unit`
- `set(group: String?, name: String?, value: Any?): Unit`

### object: fieldContext

Type: `com.itangcent.easyapi.core.rule.context.ScriptFieldPathContext` — Current field path helper.

- `path(): String`
- `property(name: String): String`

### object: httpClient

Type: `com.itangcent.easyapi.core.http.ScriptHttpClient` — Synchronous adapter to the EasyAPI HTTP client.

- `executeSync(request: com.itangcent.easyapi.core.http.HttpRequest): com.itangcent.easyapi.core.http.HttpResponse`

### object: helper

Type: `com.itangcent.easyapi.core.rule.parser.ScriptHelper` — PSI lookup helper.

- `findClass(canonicalText: String): Any?`
- `findClassByAnnotation(annotationFqn: String): Any?`
- `findClassesByAnnotation(annotationFqn: String): List<Any>`
- `findMethodsByAnnotation(annotationFqn: String): List<Any>`
- `jsonTypeToSchemaType(jsonType: String?): String`
- `resolveLink(canonicalText: String): Any?`
- `resolveLinks(canonicalText: String): List<Any>`

### object: runtime

Type: `com.itangcent.easyapi.core.rule.parser.ScriptRuntime` — Project and module metadata helper.

- `async(runnable: Runnable): Unit`
- `filePath(): String?`
- `module(): String?`
- `moduleName(): String?`
- `modulePath(): String?`
- `projectName(): String?`
- `projectPath(): String?`

### object: method

Type: `com.itangcent.easyapi.core.rule.context.MethodContext` — PSI method context.

- `ann(name: String): String?`
- `ann(name: String, attr: String): String?`
- `annMap(name: String): Map<String, Any?>?`
- `annMaps(name: String): List<Map<String, Any?>>?`
- `annValue(name: String): Any?`
- `annValue(name: String, attr: String): Any?`
- `argCnt(): Int`
- `argTypes(): Array<com.itangcent.easyapi.core.rule.context.ScriptTypeContext>`
- `args(): Array<com.itangcent.easyapi.core.rule.context.ScriptPsiParameterContext>`
- `canonicalText(): String`
- `containingClass(): com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext?`
- `contextType(): String`
- `defineClass(): com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext?`
- `defineCode(): String?`
- `doc(): String?`
- `doc(tag: String): String?`
- `doc(tag: String, subTag: String): String?`
- `docs(tag: String): List<String>?`
- `fieldName(): String`
- `getExt(name: String): Any?`
- `getName(): String?`
- `hasAnn(name: String): Boolean`
- `hasDoc(tag: String): Boolean`
- `hasModifier(modifier: String): Boolean`
- `isAbstract(): Boolean`
- `isConstructor(): Boolean`
- `isDefault(): Boolean`
- `isEnumField(): Boolean`
- `isNative(): Boolean`
- `isOverride(): Boolean`
- `isStatic(): Boolean`
- `isSynchronized(): Boolean`
- `isVarArgs(): Boolean`
- `modifiers(): List<String>`
- `name(): String`
- `paramCnt(): Int`
- `parameters(): Array<com.itangcent.easyapi.core.rule.context.ScriptPsiParameterContext>`
- `params(): Array<com.itangcent.easyapi.core.rule.context.ScriptPsiParameterContext>`
- `psi(): com.intellij.psi.PsiElement?`
- `returnType(): com.itangcent.easyapi.core.rule.context.ScriptTypeContext?`
- `setExt(name: String, value: Any?): Unit`
- `sourceCode(): String?`
- `throwsExceptions(): Array<String>`
- `type(): com.itangcent.easyapi.core.rule.context.ScriptTypeContext?`

### object: parameter

Type: `com.itangcent.easyapi.core.rule.context.ParameterContext` — PSI parameter context.

- `ann(name: String): String?`
- `ann(name: String, attr: String): String?`
- `annMap(name: String): Map<String, Any?>?`
- `annMaps(name: String): List<Map<String, Any?>>?`
- `annValue(name: String): Any?`
- `annValue(name: String, attr: String): Any?`
- `canonicalText(): String`
- `contextType(): String`
- `declaration(): com.itangcent.easyapi.core.rule.context.ScriptItContext?`
- `defineCode(): String?`
- `doc(): String?`
- `doc(tag: String): String?`
- `doc(tag: String, subTag: String): String?`
- `docs(tag: String): List<String>?`
- `getExt(name: String): Any?`
- `getName(): String?`
- `hasAnn(name: String): Boolean`
- `hasDoc(tag: String): Boolean`
- `hasModifier(modifier: String): Boolean`
- `isFinal(): Boolean`
- `isVarArgs(): Boolean`
- `jsonType(): com.itangcent.easyapi.core.rule.context.ScriptTypeContext`
- `method(): com.itangcent.easyapi.core.rule.context.ScriptPsiMethodContext?`
- `modifiers(): List<String>`
- `name(): String`
- `psi(): com.intellij.psi.PsiElement?`
- `setExt(name: String, value: Any?): Unit`
- `sourceCode(): String?`
- `type(): com.itangcent.easyapi.core.rule.context.ScriptTypeContext`

### object: field

Type: `com.itangcent.easyapi.core.rule.context.FieldContext` — PSI field context; object-model fields may also be represented by a method context.

- `ann(name: String): String?`
- `ann(name: String, attr: String): String?`
- `annMap(name: String): Map<String, Any?>?`
- `annMaps(name: String): List<Map<String, Any?>>?`
- `annValue(name: String): Any?`
- `annValue(name: String, attr: String): Any?`
- `asEnumField(): com.itangcent.easyapi.core.rule.context.ScriptPsiEnumConstantContext?`
- `canonicalText(): String`
- `constantValue(): Any?`
- `containingClass(): com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext?`
- `contextType(): String`
- `defineClass(): com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext?`
- `defineCode(): String?`
- `doc(): String?`
- `doc(tag: String): String?`
- `doc(tag: String, subTag: String): String?`
- `docs(tag: String): List<String>?`
- `getExt(name: String): Any?`
- `getName(): String?`
- `hasAnn(name: String): Boolean`
- `hasDoc(tag: String): Boolean`
- `hasModifier(modifier: String): Boolean`
- `isEnumField(): Boolean`
- `isFinal(): Boolean`
- `isStatic(): Boolean`
- `isTransient(): Boolean`
- `isVolatile(): Boolean`
- `jsonType(): com.itangcent.easyapi.core.rule.context.ScriptTypeContext`
- `modifiers(): List<String>`
- `name(): String`
- `psi(): com.intellij.psi.PsiElement?`
- `setExt(name: String, value: Any?): Unit`
- `sourceCode(): String?`
- `type(): com.itangcent.easyapi.core.rule.context.ScriptTypeContext`

### object: empty

Type: `com.itangcent.easyapi.core.rule.context.ItContext` — No PSI element is supplied; common helper bindings remain available.

- `ann(name: String): String?`
- `ann(name: String, attr: String): String?`
- `annMap(name: String): Map<String, Any?>?`
- `annMaps(name: String): List<Map<String, Any?>>?`
- `annValue(name: String): Any?`
- `annValue(name: String, attr: String): Any?`
- `canonicalText(): String`
- `contextType(): String`
- `defineCode(): String?`
- `doc(): String?`
- `doc(tag: String): String?`
- `doc(tag: String, subTag: String): String?`
- `docs(tag: String): List<String>?`
- `getExt(name: String): Any?`
- `getName(): String?`
- `hasAnn(name: String): Boolean`
- `hasDoc(tag: String): Boolean`
- `hasModifier(modifier: String): Boolean`
- `modifiers(): List<String>`
- `name(): String`
- `psi(): com.intellij.psi.PsiElement?`
- `setExt(name: String, value: Any?): Unit`
- `sourceCode(): String?`

### object: api

Type: `com.itangcent.easyapi.core.rule.context.ScriptApiEndpoint` — Script-facing mutable API endpoint.

- `appendDesc(desc: String?): Unit`
- `appendResponseBodyDesc(desc: String?): Unit`
- `description(): String?`
- `method(): String?`
- `name(): String?`
- `path(): String?`
- `setDescription(desc: String?): Unit`
- `setFormParam(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?): Unit`
- `setHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?): Unit`
- `setMethod(method: String): Unit`
- `setParam(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?): Unit`
- `setPath(path: String): Unit`
- `setPathParam(name: String?, defaultValue: String?, desc: String?, example: String?): Unit`
- `setResponseBodyClass(className: String?): Unit`
- `setResponseCode(code: Int): Unit`
- `setResponseHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?): Unit`
- `toCurl(): String`
- `toCurl(host: String): String`
- `toCurl(host: String, runPreScripts: Boolean): String`

### object: collection

Type: `kotlin.collections.List` — Read-only list-like collection of the exported endpoints (a standard List — use its usual API).

*(standard `kotlin.collections.List` — no EasyApi-specific methods; use the type's own API)*

### object: endpoint

Type: `com.itangcent.easyapi.core.rule.context.ScriptApiEndpoint` — Script-facing mutable API endpoint.

- `appendDesc(desc: String?): Unit`
- `appendResponseBodyDesc(desc: String?): Unit`
- `description(): String?`
- `method(): String?`
- `name(): String?`
- `path(): String?`
- `setDescription(desc: String?): Unit`
- `setFormParam(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?): Unit`
- `setHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?): Unit`
- `setMethod(method: String): Unit`
- `setParam(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?): Unit`
- `setPath(path: String): Unit`
- `setPathParam(name: String?, defaultValue: String?, desc: String?, example: String?): Unit`
- `setResponseBodyClass(className: String?): Unit`
- `setResponseCode(code: Int): Unit`
- `setResponseHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?): Unit`
- `toCurl(): String`
- `toCurl(host: String): String`
- `toCurl(host: String, runPreScripts: Boolean): String`

### object: request

Type: `com.itangcent.easyapi.core.http.HttpRequestWrapper` — Script-facing HTTP request wrapper.

- `body(): String?`
- `contentType(): String?`
- `cookies(): List<com.itangcent.easyapi.core.http.HttpCookie>`
- `formParams(): List<com.itangcent.easyapi.core.http.FormParam>`
- `headers(): List<com.itangcent.easyapi.core.http.KeyValue /* = Pair<String, String> */>`
- `method(): String`
- `query(): List<com.itangcent.easyapi.core.http.KeyValue /* = Pair<String, String> */>`
- `removeHeader(name: String): Unit`
- `setHeader(name: String, value: String): Unit`
- `toHttpRequest(): com.itangcent.easyapi.core.http.HttpRequest`
- `url(): String`

### object: response

Type: `com.itangcent.easyapi.core.http.HttpResponseWrapper` — Script-facing HTTP response wrapper.

- `body(): String?`
- `code(): Int`
- `discard(): Unit`
- `headers(): Map<String, List<String>>`
- `isDiscarded(): Boolean`
- `request(): com.itangcent.easyapi.core.http.HttpRequestWrapper`


---

## api.class.parse.after

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Fires after an API class is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.class.parse.before

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Fires before an API class is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.method.parse.after

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Fires after an API method is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.method.parse.before

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Fires before an API method is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.name

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
API name / title of the endpoint.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.open

**Source:** `yapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Whether the API is open/exposed (default false).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.param.parse.after

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Fires after an API parameter is parsed.

**Aliases:** `param.after`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.param.parse.before

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Fires before an API parameter is parsed.

**Aliases:** `param.before`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.status

**Source:** `yapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Lifecycle status of the API (e.g. "undone", "deprecated").

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## api.tag

**Source:** `yapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Tag(s) attached to the API (e.g. for YApi grouping/filtering).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## class.doc

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Class documentation text.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## class.is.feign.ctrl

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Whether the class is a Feign client.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## class.is.grpc

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Whether the class is a gRPC service.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## class.is.jaxrs.ctrl

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Whether the class is a JAX-RS resource.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## class.is.quarkus.ctrl

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Whether the class is a Quarkus controller.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## class.is.spring.ctrl

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Whether the class is a Spring controller.

**Aliases:** `class.is.ctrl`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## class.prefix.path

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Path prefix contributed by the class.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## constant.field.ignore

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`
Skip a constant field when true.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.class.is.api

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Whether the class is a Custom API class.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.class.parse.after

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Fires after a Custom class is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.class.parse.before

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Fires before a Custom class is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.export.after

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `api`  
**`it` context types:** `method`, `class`, `empty`
Fires after a Custom endpoint is built; api exposes export-model mutations.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'/'empty'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `api` — Script-facing mutable API endpoint. (availability: key-specific)

**Object APIs:** `method`, `class`, `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `api`

## custom.http.method

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
HTTP verb (GET/POST/…) for the method.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.method.is.api

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Whether the method is a Custom endpoint.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.method.parse.after

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Fires after a Custom method is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.method.parse.before

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Fires before a Custom method is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.as.cookie

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Bind the parameter as a cookie when true.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.as.form.body

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Bind the parameter as a form field when true.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.as.json.body

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Bind the parameter as request body when true.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.as.path.var

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Bind the parameter as a path variable when true.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.cookie

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Cookie name (when binding=cookie).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.cookie.value

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Cookie value override.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.header

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Header name (when binding=header).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.name

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Parameter name override (query/form).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.param.path.var

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Path-variable name override.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## custom.path

**Source:** `Custom`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Base path (class) / method path (method) — context-sensitive.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## endpoint.prefix.path

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Path prefix contributed to the endpoint.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## enum.use.custom

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Custom enum conversion rule.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## export.after

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `api`  
**`it` context types:** `method`, `class`, `empty`
Fires after an API endpoint is built; api exposes export-model mutations and cURL rendering.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'/'empty'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `api` — Script-facing mutable API endpoint. (availability: key-specific)

**Object APIs:** `method`, `class`, `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `api`

## field.advanced

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Advanced field metadata.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.default.value

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Default value of the field.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.demo

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Demo/example value of the field.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.doc

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Field documentation text.

**Aliases:** `doc.field`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.ignore

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Skip (strip) the field from the exported model when true.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.mock

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Mock value of the field.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.name

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Field name in the exported model.

**Aliases:** `json.rule.field.name`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.name.prefix

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Prefix prepended to the field name.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.name.suffix

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Suffix appended to the field name.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.order

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Ordering position of the field.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.order.with

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `a`, `b`  
**`it` context types:** `field`, `method`
Compares two object-model members for ordering; a and b are field or method contexts.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `a` — Injected field context value; compare/inspect it directly (no script-object API). (availability: always)
- `b` — Injected field context value; compare/inspect it directly (no script-object API). (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## field.required

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Whether the field is required.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## folder.name

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Folder/group name the endpoint is exported under.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## hopp.class.prerequest

**Source:** `hoppscotch`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Class-level Hoppscotch pre-request script (Groovy rule).

**Aliases:** `class.hopp.prerequest`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## hopp.class.test

**Source:** `hoppscotch`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Class-level Hoppscotch test script (Groovy rule).

**Aliases:** `class.hopp.test`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## hopp.collection.prerequest

**Source:** `hoppscotch`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `collection`  
**`it` context types:** `empty`
Collection-level pre-request event; exposes exported endpoints.

**Aliases:** `collection.hopp.prerequest`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `collection` — Read-only list-like collection of the exported endpoints (a standard List — use its usual API). (availability: always for this event)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `collection`

## hopp.collection.test

**Source:** `hoppscotch`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `collection`  
**`it` context types:** `empty`
Collection-level test event; exposes exported endpoints.

**Aliases:** `collection.hopp.test`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `collection` — Read-only list-like collection of the exported endpoints (a standard List — use its usual API). (availability: always for this event)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `collection`

## hopp.format.after

**Source:** `hoppscotch`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `endpoint`  
**`it` context types:** `method`
Runs after one Hoppscotch endpoint is formatted.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `endpoint` — Mutable API endpoint that produced the export item. (availability: key-specific)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `endpoint`

## hopp.host

**Source:** `hoppscotch`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`, `empty`
Base URL override for Hoppscotch endpoints.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'class'/'empty'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## hopp.prerequest

**Source:** `hoppscotch`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Generates a Hoppscotch pre-request script (Groovy rule; the script runs in Hoppscotch).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## hopp.test

**Source:** `hoppscotch`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Generates a Hoppscotch test script (Groovy rule; the script runs in Hoppscotch).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## http.call.after

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `request`, `response`  
**`it` context types:** `empty`
Runs after an HTTP response; call response.discard() to request a bounded retry.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `request` — HTTP request wrapper; header mutations are carried to the send/retry attempt. (availability: always for this event)
- `response` — HTTP response wrapper; discard() requests a bounded retry. (availability: always for this event)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `request`, `response`

## http.call.before

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `request`  
**`it` context types:** `empty`
Runs immediately before an HTTP request is sent; request headers can be mutated.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `request` — HTTP request wrapper; header mutations are carried to the send/retry attempt. (availability: always for this event)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `request`

## ignore

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`, `method`, `field`, `parameter`
Skip the element entirely when true.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'class'/'method'/'field'/'parameter'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `method`, `field`, `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## json.additional.field

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Extra JSON object field injected into the serialized response (e.g. computed totals, envelope metadata).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## json.class.parse.after

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Fires after a class is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## json.class.parse.before

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Fires before a class is parsed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## json.field.parse.after

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Fires after a field is parsed.

**Aliases:** `field.parse.after`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## json.field.parse.before

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Fires before a field is parsed.

**Aliases:** `field.parse.before`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## json.rule.convert

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Type conversion rule; the resolved type behaves as a class context for scripts.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## json.unwrapped

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `field`, `method`
Whether the field is unwrapped into the parent object.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'field'/'method'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `field`, `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## markdown.curl.host

**Source:** `implicit`  
**Execution mode:** `static-configuration`
**Bindings:**   
**`it` context types:** 
Host override for the cURL placeholder in Markdown export.

## markdown.template

**Source:** `general`  
**Execution mode:** `static-configuration`
**Bindings:**   
**`it` context types:** 
Markdown template path or remote http(s) URL.

## markdown.template.language

**Source:** `general`  
**Execution mode:** `static-configuration`
**Bindings:**   
**`it` context types:** 
Language for the Markdown template.

## markdown.template.url.max.bytes

**Source:** `implicit`  
**Execution mode:** `static-configuration`
**Bindings:**   
**`it` context types:** 
Maximum bytes for a fetched remote Markdown template.

## markdown.template.url.ttl.seconds

**Source:** `implicit`  
**Execution mode:** `static-configuration`
**Bindings:**   
**`it` context types:** 
TTL (seconds) for the cached remote Markdown template.

## max.deep

**Source:** `implicit`  
**Execution mode:** `static-configuration`
**Bindings:**   
**`it` context types:** 
Maximum parse depth for object models.

## max.elements

**Source:** `implicit`  
**Execution mode:** `static-configuration`
**Bindings:**   
**`it` context types:** 
Maximum element count for object models.

## method.additional.header

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Extra request headers (JSON object).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## method.additional.param

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Extra request params (JSON object).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## method.additional.response.header

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Extra response headers (JSON object).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## method.content.type

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Content type of the request.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## method.default.http.method

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Default HTTP method when none is detected.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## method.doc

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Method documentation text.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## method.return

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Return type of the method.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## method.return.main

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Main (unwrapped) return type of the method.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## openapi.format.after

**Source:** `openapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `document`  
**`it` context types:** `method`
Runs after the OpenAPI document is built and before it is serialized.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `document` — Mutable channel-owned document model. (availability: always for this event)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## openapi.host

**Source:** `openapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`, `empty`
Base URL override for the OpenAPI servers array (legacy).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'class'/'empty'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## openapi.info.description

**Source:** `openapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `empty`
OpenAPI document info.description override.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## openapi.info.title

**Source:** `openapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `empty`
OpenAPI document info.title override.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## openapi.info.version

**Source:** `openapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `empty`
OpenAPI document info.version override.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## openapi.server.url

**Source:** `openapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `empty`
Base URL override for the OpenAPI servers array.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.default.value

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Default value of the parameter.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.demo

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Demo/example value of the parameter.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.doc

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Parameter documentation text.

**Aliases:** `doc.param`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.http.type

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
HTTP binding type of the parameter (path/query/body/…).

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.ignore

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Skip the parameter when true.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.mock

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Mock value of the parameter.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.name

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Parameter name.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.required

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Whether the parameter is required.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## param.type

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `parameter`
Parameter type.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `parameter`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## path.multi

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`
Multiple paths for the endpoint.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## postman.class.prerequest

**Source:** `postman`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Class-level pre-request script that runs BEFORE the request — inject headers, compute signatures, mutate pm.request.

**Aliases:** `class.postman.prerequest`

**Notes:**
- Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.
- response is null before the request is sent.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## postman.class.test

**Source:** `postman`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Class-level test script that runs AFTER the response — assert on pm.response, store tokens via pm.environment.set.

**Aliases:** `class.postman.test`

**Notes:**
- Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.
- response is available only after the request completes.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## postman.collection.prerequest

**Source:** `postman`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `collection`  
**`it` context types:** `empty`
Collection-level pre-request event (Groovy rule; not a dashboard pm script).

**Aliases:** `collection.postman.prerequest`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `collection` — Read-only list-like collection of the exported endpoints (a standard List — use its usual API). (availability: always for this event)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `collection`

## postman.collection.test

**Source:** `postman`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `collection`  
**`it` context types:** `empty`
Collection-level test event (Groovy rule; not a dashboard pm script).

**Aliases:** `collection.postman.test`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `collection` — Read-only list-like collection of the exported endpoints (a standard List — use its usual API). (availability: always for this event)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `collection`

## postman.format.after

**Source:** `postman`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `item`, `endpoint`  
**`it` context types:** `method`
Runs after one Postman item is created; item and endpoint are key-specific export extensions.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)
- `item` — Mutable channel-owned export item. (availability: always for this event)
- `endpoint` — Mutable API endpoint that produced the export item. (availability: key-specific)

**Object APIs:** `method`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`, `endpoint`

## postman.host

**Source:** `postman`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`, `empty`
Base URL override for Postman requests.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'class'/'empty'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## postman.prerequest

**Source:** `postman`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Pre-request script that runs BEFORE the request — inject headers, compute signatures, mutate pm.request.

**Notes:**
- Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.
- response is null before the request is sent.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## postman.test

**Source:** `postman`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Test script that runs AFTER the response — assert on pm.response, store tokens via pm.environment.set.

**Notes:**
- Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.
- response is available only after the request completes.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## properties.prefix

**Source:** `general`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `class`
Prefix for property resolution.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## yapi.export.before

**Source:** `yapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `empty`
Fires once before the YApi export starts.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `empty`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## yapi.project

**Source:** `yapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
YApi project (token/id) the endpoint is exported under.

**Aliases:** `project`, `module`

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## yapi.save.after

**Source:** `yapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Fires after an endpoint is uploaded to YApi; the upload result is exposed.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

## yapi.save.before

**Source:** `yapi`  
**Execution mode:** `dynamic`
**Bindings:** `it`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`  
**`it` context types:** `method`, `class`
Fires before an endpoint is uploaded to YApi; the document can be mutated.

**Bindings:**
- `it` — Current rule context. Its concrete type depends on this rule key. Discriminate with it.contextType(), which returns 'method'/'class'. (availability: always)
- `logger` (aliases: `LOG`) — EasyAPI console logger. (availability: always)
- `session` (aliases: `S`, `sessionStorage`) — Operation session storage. (availability: always)
- `tool` (aliases: `T`) — RuleToolUtils conversion, collection, JSON, string, and date helpers. (availability: always)
- `regex` (aliases: `RE`) — Regular-expression helpers. (availability: always)
- `files` (aliases: `F`) — File save helper. (availability: always)
- `config` (aliases: `C`) — Resolved EasyAPI configuration values. (availability: always)
- `localStorage` — Persistent local storage. (availability: always)
- `fieldContext` — Current object-model field path. (availability: always)
- `httpClient` — Blocking HTTP adapter for Groovy rules. (availability: when an HTTP client is configured)
- `helper` (aliases: `H`) — PSI class and documentation-link lookup helper. (availability: always)
- `runtime` (aliases: `R`) — Project, module, and source-file metadata. (availability: always)

**Object APIs:** `method`, `class`, `logger`, `session`, `tool`, `regex`, `files`, `config`, `localStorage`, `fieldContext`, `httpClient`, `helper`, `runtime`

