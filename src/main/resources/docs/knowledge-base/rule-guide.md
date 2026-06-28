# EasyApi Rule Authoring Guide

This guide describes the **rule file format** used by EasyApi to customize API documentation extraction — naming, descriptions, type conversion, filtering, and lifecycle hooks. Rules are plain text files (`.rules` or `.properties`-style) loaded by the [Config Reader](#) at startup and reloaded when settings change.

> **AI-assisted authoring:** Open the **Rules** tab and click **Chat** or **Magic** (bottom action bar) to reveal the inline AI assistant. Describe what you want in natural language; the assistant reads your existing rules, proposes new content, and saves it. See [AI-assisted rule creation](#ai-assisted-rule-creation).

---

## Table of Contents

1. [File Format](#file-format)
2. [Rule Key Catalog](#rule-key-catalog)
3. [Filter Syntax](#filter-syntax)
4. [Expression Prefixes](#expression-prefixes)
5. [Aggregation Modes](#aggregation-modes)
6. [Groovy Binding Reference](#groovy-binding-reference)
7. [Recipes](#recipes)
8. [Migrating from the Built-in Tab](#migrating-from-the-built-in-tab)
9. [AI-assisted rule creation](#ai-assisted-rule-creation)

---

## File Format

A rule file is a UTF-8 text file with one rule per line:

```
# Comments start with #
# Format: <key>[<filter>]=<value>
#   filter   — optional; goes INSIDE [...] AFTER the key
#   key      — a rule key from the catalog below
#   value    — literal text, expression, or script

# A rule with no filter applies to every element:
api.status=disabled

# A rule with a filter applies only to matching elements:
api.tag[$class:com.example.UserController]=user
```

### Filter

The filter goes **inside `[...]` after the key** — there is no `filter?key=value` form. It restricts the rule to elements that match. See [Filter Syntax](#filter-syntax) below.

### Quoting & escaping

- Values are read literally up to the end of the line (trimmed).
- Leading/trailing whitespace around `=` is stripped.
- `#` at the start of a line is a comment — to use `#` as a literal value prefix, ensure there's no leading whitespace before the key.

### Multiple values (merge keys)

Some keys (marked `MERGE` / `MERGE_DISTINCT` in the catalog) accumulate values across rules. For example, `api.tag` with `MERGE_DISTINCT` collects all tags from matching rules:

```
api.tag=user
api.tag=admin
```

yields `[user, admin]`.

---

## Rule Key Catalog

Every key below is sourced from [RuleKeys.kt](../../src/main/kotlin/com/itangcent/easyapi/rule/RuleKeys.kt) and matches the output of the `list_rule_keys` AI tool.

### API metadata

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `api.name` | string | replace | API endpoint display name |
| `api.tag` | string | merge-distinct | Tags/categories applied to the endpoint |
| `api.status` | string | replace | Status label (e.g., `deprecated`, `disabled`) |
| `api.open` | boolean | replace | Whether the endpoint is publicly open |
| `folder.name` | string | replace | Folder/group name in the exported tree |
| `ignore` | boolean | replace | Skip this element entirely |

### Method rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `method.doc` | string | merge-distinct | Method-level documentation |
| `class.doc` | string | merge-distinct | Class-level documentation |
| `method.default.http.method` | string | replace | Default HTTP verb when not annotated |
| `method.content.type` | string | replace | Request content type |
| `method.return` | string | replace | Return type override (full name) |
| `method.return.main` | string | replace | Inner type when returning a wrapper (e.g., `Mono<T>`) |
| `class.prefix.path` | string | replace | Path prefix for all endpoints in the class |
| `endpoint.prefix.path` | string | replace | Path prefix for a specific endpoint |
| `path.multi` | string | replace | Configures multi-path handling |

### Parameter rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `param.name` | string | replace | Parameter display name |
| `param.type` | string | replace | Parameter type override |
| `param.required` | boolean | replace | Whether the parameter is required |
| `param.ignore` | boolean | replace | Skip this parameter |
| `param.default.value` | string | replace | Default value for the parameter |
| `param.doc` | string | merge-distinct | Parameter documentation (alias: `doc.param`) |
| `param.http.type` | string | replace | HTTP param location (query/path/header/cookie/body) |
| `param.demo` | string | replace | Demo value for the parameter |
| `param.mock` | string | replace | Mock value for the parameter |

### Field rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `field.name` | string | replace | Field display name (alias: `json.rule.field.name`) |
| `field.name.prefix` | string | replace | Prefix added to all field names in a class |
| `field.name.suffix` | string | replace | Suffix added to all field names in a class |
| `field.required` | boolean | replace | Whether the field is required |
| `field.ignore` | boolean | replace | Skip this field |
| `field.default.value` | string | replace | Default value for the field |
| `field.doc` | string | merge-distinct | Field documentation (alias: `doc.field`) |
| `field.demo` | string | replace | Demo value for the field |
| `field.order` | string | replace | Field ordering hint |
| `field.order.with` | string | replace | Companion fields for ordering |
| `field.mock` | string | replace | Mock value for the field |
| `field.advanced` | string | merge | Advanced field metadata |

### JSON rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `json.field.parse.before` | event | — | Hook fired before each field is parsed |
| `json.field.parse.after` | event | — | Hook fired after each field is parsed |
| `json.class.parse.before` | event | — | Hook fired before a class is parsed into JSON |
| `json.class.parse.after` | event | — | Hook fired after a class is parsed into JSON |
| `json.additional.field` | string | merge | Inject an additional field into the JSON schema |
| `json.rule.convert` | string | replace | Type conversion rule (regex on the type name) |
| `json.unwrapped` | boolean | replace | Whether the field is unwrapped (Jackson `@JsonUnwrapped`) |

### API lifecycle events

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `api.class.parse.before` | event | — | Hook fired before a controller class is parsed |
| `api.class.parse.after` | event | — | Hook fired after a controller class is parsed |
| `api.method.parse.before` | event | — | Hook fired before each API method is parsed |
| `api.method.parse.after` | event | — | Hook fired after each API method is parsed |
| `api.param.parse.before` | event | — | Hook fired before parameters are parsed (alias: `param.before`) |
| `api.param.parse.after` | event | — | Hook fired after parameters are parsed (alias: `param.after`) |
| `export.after` | event | — | Hook fired after the full export completes |

### Additional headers / params

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `method.additional.header` | string | merge | Extra request headers to add |
| `method.additional.param` | string | merge | Extra request parameters to add |
| `method.additional.response.header` | string | merge | Extra response headers to add |

### HTTP call events

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `http.call.before` | event | — | Hook fired before every HTTP call |
| `http.call.after` | event | — | Hook fired after every HTTP call |

### Class recognizer rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `class.is.spring.ctrl` | boolean | replace | Whether the class is a Spring controller (alias: `class.is.ctrl`) |
| `class.is.feign.ctrl` | boolean | replace | Whether the class is a Feign client |
| `class.is.jaxrs.ctrl` | boolean | replace | Whether the class is a JAX-RS resource |
| `class.is.quarkus.ctrl` | boolean | replace | Whether the class is a Quarkus resource |
| `class.is.grpc` | boolean | replace | Whether the class is a gRPC service |

### Postman rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `postman.prerequest` | string | merge | Pre-request script (Postman) |
| `postman.class.prerequest` | string | merge | Class-level pre-request script (alias: `class.postman.prerequest`) |
| `postman.collection.prerequest` | event | — | Collection-level pre-request script (alias: `collection.postman.prerequest`) |
| `postman.test` | string | merge | Post-response test script |
| `postman.class.test` | string | merge | Class-level test script (alias: `class.postman.test`) |
| `postman.collection.test` | event | — | Collection-level test script (alias: `collection.postman.test`) |
| `postman.host` | string | replace | Host override for Postman export |
| `postman.format.after` | event | throw-in-error | Hook fired after Postman collection formatting |

### YApi rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `yapi.project` | string | replace | YApi project token (aliases: `project`, `module`) |
| `yapi.export.before` | event | throw-in-error | Hook fired before YApi export |
| `yapi.save.before` | event | throw-in-error | Hook fired before each YApi save |
| `yapi.save.after` | event | throw-in-error | Hook fired after each YApi save |

### Enum / constant rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `enum.use.custom` | string | replace | Custom enum value provider script |
| `constant.field.ignore` | boolean | replace | Skip constant fields |

### Properties rules

| Key | Type | Mode | Description |
|-----|------|------|-------------|
| `properties.prefix` | string | replace | Prefix applied to properties file keys |

---

## Filter Syntax

Filters appear **inside `[...]` after the key** and restrict the rule to matching elements. A filter is a single expression that the rule engine evaluates against the current PSI element (class, method, field, parameter, or type).

```
<key>[<filter>]=<value>
```

### Examples

```
# Apply to all elements of the class UserController
api.tag[$class:com.example.UserController]=user

# Apply to fields whose name matches a regex
field.name[#regex:.*List.*]=${it.name}s

# Apply to methods annotated with @Deprecated
api.status[@org.springframework.lang.Deprecated]=deprecated

# Apply to classes tagged #internal
api.status[#internal]=internal
```

---

## Expression Prefixes

The rule engine dispatches an expression to a parser based on its prefix. The following prefixes are supported:

| Prefix | Parser | Description | Example |
|--------|--------|-------------|---------|
| `$class:` | ClassMatchParser | Exact fully-qualified class-name match (no wildcards). For package/pattern matching use `groovy:`. | `$class:com.example.UserController` |
| `@` | AnnotationExpressionParser | Matches elements annotated with the given annotation | `@org.springframework.web.bind.annotation.RestController` |
| `#regex:` | RegexParser | Matches by regex; captured groups available as `${1}`, `${2}` in the value | `#regex:Mono<(.*?)>` |
| `#` | TagExpressionParser | Matches elements tagged with the given tag (from comments or annotations) | `#internal` |
| `!` | NegationParser | Negates the following expression | `!@java.lang.Deprecated` |
| `groovy:` | GroovyScriptParser | Runs a Groovy script; truthy result = match | `groovy: it.hasAnn("X")` |
| *(none)* | LiteralParser | A literal string value (no filter — always matches) | `api.status=disabled` |

> There is **no** `~` prefix and **no** bare `class:` prefix. The older
> `class:com.example.Foo` and `~regex` forms are invalid — use `$class:` and
> `#regex:` respectively. `$class:` does exact matches only; for package or
> pattern matching use `groovy:` (e.g.
> `groovy: it.containingClass()?.name()?.startsWith("com.example.web.")`).

### Capture groups (`#regex:`)

When a `#regex:` filter matches, the captured groups are available in the value via `${1}`, `${2}`, etc., and in scripts via `it.regexGroups`.

---

## Aggregation Modes

When multiple rules match the same element, the aggregation mode determines how the values combine:

| Mode | Behavior |
|------|----------|
| `REPLACE` (default) | Last matching value wins; previous values are discarded. |
| `MERGE` | All matching values are concatenated (in rule-file order). |
| `MERGE_DISTINCT` | All matching values are collected, duplicates removed (order preserved). |

### Event rules

Event keys (lifecycle hooks) are always `MERGE` — every matching rule fires, in rule-file order.

---

## Groovy Binding Reference

When a rule value is prefixed with `groovy:`, it runs as a Groovy script via the JSR-223 engine. The following variables are bound:

| Variable | Alias | Type | Description |
|----------|-------|------|-------------|
| `it` | — | ScriptContext | The current element (class, method, field, parameter, or type). Provides `name()`, `ann()`, `doc()`, `containingClass()`, etc. |
| `logger` | `LOG` | IdeaConsole | Logging utility. `logger.info(...)`, `logger.warn(...)`, `logger.error(...)` |
| `session` | `S`, `sessionStorage` | ScriptStorageWrapper | In-memory key-value store scoped to the current session. `session.get(group, key)`, `session.set(group, key, value)`, `session.pop(...)`, `session.push(...)`, `session.peek(...)` |
| `tool` | `T` | RuleToolUtils | Utility functions. `tool.toJson(...)`, `tool.fromJson(...)`, string helpers. |
| `regex` | `RE` | RegexUtils | Regex utilities. `regex.match(input, pattern)`, `regex.findGroup(input, pattern, group)`. |
| `files` | `F` | ScriptFilesWrapper | File operations. `files.save(path, content)`, `files.saveWithUI(content)`. |
| `config` | `C` | ScriptConfigWrapper | Config reader access. `config.get(key)`, `config.getValues(key)`, `config.resolveProperty(text)`. |
| `helper` | `H` | ScriptHelper | Class lookup utilities. `helper.findClass("com.example.User")`, `helper.resolveLink(...)`, `helper.jsonTypeToYapiType("int")`. |
| `runtime` | `R` | ScriptRuntime | Project/module metadata. `runtime.projectName()`, `runtime.module()`, `runtime.filePath()`. |
| `httpClient` | — | HttpClient | HTTP client for outbound calls (may be `null` if initialization failed). |
| `localStorage` | — | ScriptStorageWrapper | Persistent key-value store (SQLite-backed). Same API as `session`. |
| `fieldContext` | — | ScriptFieldPathContext | When evaluating field rules, the dotted field path (e.g., `user.address.street`). |

### The `it` object

`it` is the central object in every script. It wraps the current PSI element and exposes a script-friendly API:

```groovy
// Name of the current element
it.name()

// Fully-qualified name of the containing class
it.containingClass().name()

// Annotation access — returns the annotation wrapper or null
it.ann("org.springframework.web.bind.annotation.RequestMapping")?.path()
it.ann("org.springframework.web.bind.annotation.RequestMapping")?.method()

// Documentation text
it.doc()

// Has annotation?
it.hasAnn("java.lang.Deprecated")
```

---

## When do you need a custom rule?

**Most projects do not need custom rules.** EasyApi understands standard HTTP
frameworks out of the box — Spring MVC (`@RestController`, `@RequestMapping`,
`@GetMapping`, …), Spring WebFlux, JAX-RS (`@Path`, `@GET`, …), and Feign
clients. If your project uses one of these, export works without any rule
files.

You need a custom rule only when the scanner cannot see something that changes
the **request or response contract invisibly** — for example a servlet filter
that requires a header on every request, or a `@ControllerAdvice` that wraps
every response in a common envelope.

The catalog below lists the most common patterns, how to **detect** them, and
the **rule recipe** to use. The AI assistant (Rules tab → **Magic**) follows
the same catalog when scanning your project.

## Custom-Pattern Catalog

| Pattern | Detection signal (FQN / shape to search for) | Rule recipe |
|---------|----------------------------------------------|-------------|
| **Filter / Interceptor requiring a header** | `jakarta.servlet.Filter`, `jakarta.servlet.http.HttpFilter`, `javax.servlet.Filter`, `org.springframework.web.servlet.HandlerInterceptor` — implementations often call `request.getHeader("X-…")` in `doFilter` / `preHandle`. Search for `extends`/`implements` these types and confirm the FQN by resolving the import. | Add the header to every endpoint (no filter — applies globally): `method.additional.header={"name":"X-My-Header","value":"required-value","desc":"","required":true}`, or scope it: `method.additional.header[groovy: it.containingClass()?.name()?.startsWith("com.example.web.")]={"name":"X-My-Header","value":"\${value}","desc":"","required":true}`. |
| **WebFilter (Spring WebFlux)** | `org.springframework.web.server.WebFilter` — `filter()` that inspects `ServerHttpRequest`. | Same as above — `method.additional.header={…}`. |
| **Response wrapper (`@ControllerAdvice` + `ResponseBodyAdvice`)** | `org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice` — implementations wrap the body in `{ code, data, msg }`. Search for implementors; check the `beforeBodyWrite` return type. | Unwrap the response for documentation: `json.rule.convert[#regex:com\.example\.common\.ApiResult<(.*?)>]=${1}` (repeat for each wrapper type). |
| **Custom argument resolver (injects hidden param)** | `org.springframework.web.method.support.HandlerMethodArgumentResolver` — `supportsParameter` checks for a custom annotation or type; `resolveArgument` returns the value. The parameter is **not** declared in the source signature. | Inject the param into the docs: `api.param.parse.before[groovy: it.containingClass()?.name()?.startsWith("com.example.web.")]=com.example.CurrentUser:current_user:the current user`. Or use `api.param.parse.before[@com.example.CurrentUser]=…` if annotated. |
| **Meta-annotations (composed `@RequestMapping`)** | A custom annotation like `@GetRestApi` that is itself annotated `@GetMapping`. Search for annotations meta-annotated with `@org.springframework.web.bind.annotation.RequestMapping`. | Tell EasyApi to treat the custom annotation as a controller method marker: `class.is.spring.ctrl=groovy: it.hasAnn("com.example.GetRestApi")`. |
| **Field naming convention (snake_case / camelCase mismatch)** | The DTO uses `camelCase`, but the API contract is `snake_case` (or vice-versa). Often visible in Jackson `@JsonNaming` or a `PropertyNamingStrategy`. | `field.name[groovy: it.containingClass()?.name()?.startsWith("com.example.dto.")]=groovy: it.name().replaceAll("([A-Z])") { "_" + it[1].toLowerCase() }` |
| **Field ignores (sensitive fields)** | Specific fields that should never appear in exports. **Never blanket-ignore by name pattern** (e.g. `.*password.*`) — `password`/`secret`/`token` are often legitimate API inputs. | `field.ignore[groovy: it.name() == "internalCache" && it.containingClass()?.name() == "com.example.UserDto"]=true` |
| **Required / mock / default for a field** | A field is always required by the API but has no `@NotNull` in source, or needs a mock value for the exported example. | `field.required[groovy: it.containingClass()?.name()?.startsWith("com.example.dto.")]=name,email` · `field.mock[$class:com.example.dto.User]=groovy: it.name()=="age" ? 18 : null` |
| **Path prefix per module / package** | Every controller in `com.example.admin` should be prefixed `/admin` in the docs, even if `@RequestMapping` doesn't declare it. | `class.prefix.path[groovy: it.name()?.startsWith("com.example.admin.")]=/admin` |
| **Enum representation** | The API exposes an enum as `{ "name": "ACTIVE", "value": 1 }` but EasyApi exports just the name. | `json.rule.convert[$class:com.example.Status]={"name":"${it.name()}","value":${it.ann("com.example.Code")?.value()}}` |
| **Status / version tag** | Every endpoint in `v2` package should carry `api.status=v2` or an `api.tag=v2`. | `api.tag[groovy: it.containingClass()?.name()?.startsWith("com.example.v2.")]=v2` |
| **`@RequirePermission("admin")` → tag / header** | A custom security annotation that should become an `api.tag` or a Postman header. Search the codebase for `@com.example.RequirePermission` (resolve imports to confirm the FQN). | `api.tag[@com.example.RequirePermission]=admin` · `method.additional.header[@com.example.RequirePermission]={"name":"X-Permission","value":"\${it.ann(\"com.example.RequirePermission\")?.value()}","desc":"","required":true}` |

> **Detection tip for the AI assistant:** before proposing a rule, search the
> codebase for each FQN above (annotations via `@Fqn`, supertypes via
> `extends`/`implements`), then read each hit to confirm the pattern. Ask:
> *does it change the request/response contract invisibly?* If yes, apply
> the recipe. If no, no rule is needed. The detection tools differ by runtime
> — the built-in IntelliJ agent uses its PSI tools; the external skill uses
> file/grep search — but the rule recipe produced is the same.

---

## Recipes

### 1. Rename an API endpoint

```
api.name[groovy: it.name() == "getUserName" && it.containingClass()?.name() == "com.example.UserCtrl"]=Fetch User
```

### 2. Tag all endpoints in a controller

```
api.tag[groovy: it.containingClass()?.name() == "com.example.UserCtrl"]=user
api.tag[groovy: it.containingClass()?.name() == "com.example.UserCtrl"]=admin
```

### 3. Mark deprecated methods

```
api.status[@java.lang.Deprecated]=deprecated
```

> Note: the built-in extension already sets `api.status=deprecated` for
> `@Deprecated` methods — this recipe illustrates the filter syntax only.

### 4. Ignore a specific field from serialization

```
field.ignore[groovy: it.name() == "internalCache" && it.containingClass()?.name() == "com.example.UserDto"]=true
```

### 5. Add a prefix to all fields in a DTO package

```
field.name.prefix[groovy: it.containingClass()?.name()?.startsWith("com.example.dto.")]=prop_
```

### 6. Custom type conversion for Reactor types

```
json.rule.convert[#regex:reactor\.core\.publisher\.Mono<(.*?)>]=${1}
```

### 7. Add a pre-request script for Postman export

```
postman.prerequest[groovy: it.containingClass()?.name() == "com.example.UserCtrl"]=pm.environment.set("ts", System.currentTimeMillis())
```

### 8. Conditional ignore via Groovy filter

```
ignore[groovy: it.hasAnn("java.lang.Deprecated")]=true
```

### 9. Inject an additional field into a class

```
json.additional.field[$class:com.example.UserInfo]={"name":"version","type":"string","desc":"API version"}
```

### 10. Log every HTTP call

```
http.call.before=groovy: logger.info("HTTP ${it.method()} ${it.path()}")
```

---

## Migrating from the Built-in Tab

Before EasyApi 3.0, rule editing happened in a dedicated "Built-in" tab inside the Rules settings. That tab has been **removed**. Migration steps:

1. **Locate your built-in rules.** In 3.0, all rules live in rule files registered under **Settings → Rules → Project** or **Global**. Create a new rule file (e.g., `my-team.rules`) and add it to the list.
2. **Paste your rules** into the new file using the format above.
3. **Delete the old built-in config** — it is no longer read.
4. **Verify** by exporting an API; the rules should take effect immediately (the config reader reloads on save).

### Common gotchas

- **Filter syntax changed:** 2.x accepted a bare `class:com.example.Foo` form. 3.0 requires the `$class:` prefix (exact match, no wildcards); the bare `class:` form is invalid. Package/pattern matching is done with `groovy:` filters. Filters also moved **inside `[...]` after the key** — there is no longer a `filter?key=value` form.
- **Aliases preserved:** `doc.param`, `doc.field`, `json.rule.field.name`, `class.is.ctrl`, `project`, `module` all still work.
- **Merge modes added:** 2.x used "last wins" for everything. 3.0 introduces `MERGE` / `MERGE_DISTINCT` — see [Aggregation Modes](#aggregation-modes).

---

## AI-assisted rule creation

EasyApi 3.0 includes an AI assistant that can author rules for you. The assistant:

1. **Reads your existing rules** via perception tools (`list_rule_keys`, `get_existing_rules`, `read_rule_file`).
2. **Proposes new rule content** via the `propose_rule_content` action tool.
3. **Asks for approval** before any state-changing action (you can Approve/Reject inline).
4. **Saves the proposal** to a rule file you choose (Global or Project scope), then reloads the config so the new rules take effect immediately.

### Getting started

1. Configure an AI provider in **Settings → EasyApi → AI** (the dedicated AI tab).
2. Click **Test Connection** to verify the provider works.
3. Open the **Rules** tab and click **Chat** (bottom action bar) to reveal the inline AI panel, or **Magic** to run a built-in review-and-detect instruction.
4. Describe what you want (e.g., "Rename all endpoints in `UserController` to start with `fetch_`").
5. Review the proposal card, edit if needed, and click **Save…** — choose Global (`~/.easyapi/`) or Project (`<project>/.easyapi/`) scope + filename.

### Tips

- The assistant has access to this guide via the `get_plugin_doc` tool, so it knows the full rule key catalog.
- You can ask it to "list my current rules" before proposing changes.
- The assistant never writes files without your approval — every action tool requires an explicit Approve click.
- The assistant addresses rule files by **name** (e.g. `security.properties`), not absolute path — it does not know your home directory. `read_rule_file` resolves the name against the tracked `.easyapi/` folders. If it asks to read a file outside those folders, you get an inline Approve/Reject card to grant one-time consent.
- The **Magic** button also asks the assistant to detect custom framework patterns (Filter / Interceptor / ResponseBodyAdvice / HandlerMethodArgumentResolver / meta-annotations) that lack a rule. See the [Custom-Pattern Catalog](#custom-pattern-catalog) above.
