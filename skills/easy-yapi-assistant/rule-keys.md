# EasyApi Rule-Key Catalog

Automatically generated from every rule key's self-describing scheme (`RuleKeySchemeExporter`). Do **not** edit by hand — run `./gradlew syncRuleKeySchemes`.

**Never invent keys not listed here** — unknown keys are silently ignored.

## Legend

- **Type** — `StringKey` / `BooleanKey` / `EventKey` / `IntKey`
- **Mode** — `SINGLE` (replace) / `MERGE` / `MERGE_DISTINCT` / `ANY` / `ALL` / `IGNORE_ERROR` / `THROW_IN_ERROR`
- **Context kinds** — `empty` / `class` / `method` / `field` / `parameter`

## General rules

| Key | Type | Mode | Context | Aliases | Summary |
|-----|------|------|---------|---------|---------|
| `api.class.parse.after` | EventKey | `IGNORE_ERROR` | `CLASS` | `` | Fires after an API class is parsed. |
| `api.class.parse.before` | EventKey | `IGNORE_ERROR` | `CLASS` | `` | Fires before an API class is parsed. |
| `api.method.parse.after` | EventKey | `IGNORE_ERROR` | `METHOD` | `` | Fires after an API method is parsed. |
| `api.method.parse.before` | EventKey | `IGNORE_ERROR` | `METHOD` | `` | Fires before an API method is parsed. |
| `api.name` | StringKey | `SINGLE` | `METHOD`/`CLASS` | `` | API name / title of the endpoint. |
| `api.param.parse.after` | EventKey | `IGNORE_ERROR` | `PARAMETER` | `param.after` | Fires after an API parameter is parsed. |
| `api.param.parse.before` | EventKey | `IGNORE_ERROR` | `PARAMETER` | `param.before` | Fires before an API parameter is parsed. |
| `class.doc` | StringKey | `MERGE_DISTINCT` | `CLASS` | `` | Class documentation text. |
| `class.is.feign.ctrl` | BooleanKey | `ANY` | `CLASS` | `` | Whether the class is a Feign client. |
| `class.is.grpc` | BooleanKey | `ANY` | `CLASS` | `` | Whether the class is a gRPC service. |
| `class.is.jaxrs.ctrl` | BooleanKey | `ANY` | `CLASS` | `` | Whether the class is a JAX-RS resource. |
| `class.is.quarkus.ctrl` | BooleanKey | `ANY` | `CLASS` | `` | Whether the class is a Quarkus controller. |
| `class.is.spring.ctrl` | BooleanKey | `ANY` | `CLASS` | `class.is.ctrl` | Whether the class is a Spring controller. |
| `class.prefix.path` | StringKey | `SINGLE` | `CLASS` | `` | Path prefix contributed by the class. |
| `constant.field.ignore` | BooleanKey | `ANY` | `FIELD` | `` | Skip a constant field when true. |
| `endpoint.prefix.path` | StringKey | `SINGLE` | `METHOD` | `` | Path prefix contributed to the endpoint. |
| `enum.use.custom` | StringKey | `SINGLE` | `CLASS` | `` | Custom enum conversion rule. |
| `export.after` | EventKey | `IGNORE_ERROR` | `METHOD`/`CLASS`/`EMPTY` | `` | Fires after an API endpoint is built; api exposes export-model mutations and cURL rendering. |
| `field.advanced` | StringKey | `MERGE` | `FIELD`/`METHOD` | `` | Advanced field metadata. |
| `field.default.value` | StringKey | `SINGLE` | `FIELD`/`METHOD` | `` | Default value of the field. |
| `field.demo` | StringKey | `SINGLE` | `FIELD`/`METHOD` | `` | Demo/example value of the field. |
| `field.doc` | StringKey | `MERGE_DISTINCT` | `FIELD`/`METHOD` | `doc.field` | Field documentation text. |
| `field.ignore` | BooleanKey | `ANY` | `FIELD`/`METHOD` | `` | Skip (strip) the field from the exported model when true. |
| `field.mock` | StringKey | `SINGLE` | `FIELD`/`METHOD` | `` | Mock value of the field. |
| `field.name` | StringKey | `SINGLE` | `FIELD`/`METHOD` | `json.rule.field.name` | Field name in the exported model. |
| `field.name.prefix` | StringKey | `SINGLE` | `FIELD`/`METHOD` | `` | Prefix prepended to the field name. |
| `field.name.suffix` | StringKey | `SINGLE` | `FIELD`/`METHOD` | `` | Suffix appended to the field name. |
| `field.order` | StringKey | `SINGLE` | `FIELD`/`METHOD` | `` | Ordering position of the field. |
| `field.order.with` | StringKey | `MERGE` | `FIELD`/`METHOD` | `` | Compares two object-model members for ordering; a and b are field or method contexts. |
| `field.required` | BooleanKey | `ANY` | `FIELD`/`METHOD` | `` | Whether the field is required. |
| `folder.name` | StringKey | `SINGLE` | `METHOD`/`CLASS` | `` | Folder/group name the endpoint is exported under. |
| `http.call.after` | EventKey | `IGNORE_ERROR` | `EMPTY` | `` | Runs after an HTTP response; call response.discard() to request a bounded retry. |
| `http.call.before` | EventKey | `IGNORE_ERROR` | `EMPTY` | `` | Runs immediately before an HTTP request is sent; request headers can be mutated. |
| `ignore` | BooleanKey | `ANY` | `CLASS`/`METHOD`/`FIELD`/`PARAMETER` | `` | Skip the element entirely when true. |
| `json.additional.field` | StringKey | `MERGE` | `FIELD`/`METHOD` | `` | Extra JSON object field injected into the serialized response (e.g. computed totals, envelope metadata). |
| `json.class.parse.after` | EventKey | `IGNORE_ERROR` | `CLASS` | `` | Fires after a class is parsed. |
| `json.class.parse.before` | EventKey | `IGNORE_ERROR` | `CLASS` | `` | Fires before a class is parsed. |
| `json.field.parse.after` | EventKey | `IGNORE_ERROR` | `FIELD`/`METHOD` | `field.parse.after` | Fires after a field is parsed. |
| `json.field.parse.before` | EventKey | `IGNORE_ERROR` | `FIELD`/`METHOD` | `field.parse.before` | Fires before a field is parsed. |
| `json.rule.convert` | StringKey | `SINGLE` | `CLASS` | `` | Type conversion rule; the resolved type behaves as a class context for scripts. |
| `json.unwrapped` | BooleanKey | `ANY` | `FIELD`/`METHOD` | `` | Whether the field is unwrapped into the parent object. |
| `markdown.template` | StringKey | `SINGLE` | `—` | `` | Markdown template path or remote http(s) URL. |
| `markdown.template.language` | StringKey | `SINGLE` | `—` | `` | Language for the Markdown template. |
| `method.additional.header` | StringKey | `MERGE` | `METHOD` | `` | Extra request headers (JSON object). |
| `method.additional.param` | StringKey | `MERGE` | `METHOD` | `` | Extra request params (JSON object). |
| `method.additional.response.header` | StringKey | `MERGE` | `METHOD` | `` | Extra response headers (JSON object). |
| `method.content.type` | StringKey | `SINGLE` | `METHOD` | `` | Content type of the request. |
| `method.default.http.method` | StringKey | `SINGLE` | `METHOD` | `` | Default HTTP method when none is detected. |
| `method.doc` | StringKey | `MERGE_DISTINCT` | `METHOD` | `` | Method documentation text. |
| `method.return` | StringKey | `SINGLE` | `METHOD` | `` | Return type of the method. |
| `method.return.main` | StringKey | `SINGLE` | `METHOD` | `` | Main (unwrapped) return type of the method. |
| `param.default.value` | StringKey | `SINGLE` | `PARAMETER` | `` | Default value of the parameter. |
| `param.demo` | StringKey | `SINGLE` | `PARAMETER` | `` | Demo/example value of the parameter. |
| `param.doc` | StringKey | `MERGE_DISTINCT` | `PARAMETER` | `doc.param` | Parameter documentation text. |
| `param.http.type` | StringKey | `SINGLE` | `PARAMETER` | `` | HTTP binding type of the parameter (path/query/body/…). |
| `param.ignore` | BooleanKey | `ANY` | `PARAMETER` | `` | Skip the parameter when true. |
| `param.mock` | StringKey | `SINGLE` | `PARAMETER` | `` | Mock value of the parameter. |
| `param.name` | StringKey | `SINGLE` | `PARAMETER` | `` | Parameter name. |
| `param.required` | BooleanKey | `ANY` | `PARAMETER` | `` | Whether the parameter is required. |
| `param.type` | StringKey | `SINGLE` | `PARAMETER` | `` | Parameter type. |
| `path.multi` | StringKey | `SINGLE` | `METHOD` | `` | Multiple paths for the endpoint. |
| `properties.prefix` | StringKey | `SINGLE` | `CLASS` | `` | Prefix for property resolution. |

> `json.additional.field` value must be a valid JSON object (single line).
> `method.additional.header` value must be a valid JSON object (single line).
> `method.additional.param` value must be a valid JSON object (single line).
> `method.additional.response.header` value must be a valid JSON object (single line).

## yapi rules

| Key | Type | Mode | Context | Aliases | Summary |
|-----|------|------|---------|---------|---------|
| `api.open` | BooleanKey | `ANY` | `METHOD` | `` | Whether the API is open/exposed (default false). |
| `api.status` | StringKey | `SINGLE` | `METHOD` | `` | Lifecycle status of the API (e.g. "undone", "deprecated"). |
| `api.tag` | StringKey | `MERGE_DISTINCT` | `METHOD` | `` | Tag(s) attached to the API (e.g. for YApi grouping/filtering). |
| `yapi.export.before` | EventKey | `THROW_IN_ERROR` | `EMPTY` | `` | Fires once before the YApi export starts. |
| `yapi.project` | StringKey | `SINGLE` | `METHOD`/`CLASS` | `project`, `module` | YApi project (token/id) the endpoint is exported under. |
| `yapi.save.after` | EventKey | `THROW_IN_ERROR` | `METHOD`/`CLASS` | `` | Fires after an endpoint is uploaded to YApi; the upload result is exposed. |
| `yapi.save.before` | EventKey | `THROW_IN_ERROR` | `METHOD`/`CLASS` | `` | Fires before an endpoint is uploaded to YApi; the document can be mutated. |

## Custom Framework rules

| Key | Type | Mode | Context | Aliases | Summary |
|-----|------|------|---------|---------|---------|
| `custom.class.is.api` | BooleanKey | `ANY` | `CLASS` | `` | Whether the class is a Custom API class. |
| `custom.class.parse.after` | EventKey | `IGNORE_ERROR` | `CLASS` | `` | Fires after a Custom class is parsed. |
| `custom.class.parse.before` | EventKey | `IGNORE_ERROR` | `CLASS` | `` | Fires before a Custom class is parsed. |
| `custom.export.after` | EventKey | `IGNORE_ERROR` | `METHOD`/`CLASS`/`EMPTY` | `` | Fires after a Custom endpoint is built; api exposes export-model mutations. |
| `custom.http.method` | StringKey | `SINGLE` | `METHOD`/`CLASS` | `` | HTTP verb (GET/POST/…) for the method. |
| `custom.method.is.api` | BooleanKey | `ANY` | `METHOD`/`CLASS` | `` | Whether the method is a Custom endpoint. |
| `custom.method.parse.after` | EventKey | `IGNORE_ERROR` | `METHOD` | `` | Fires after a Custom method is parsed. |
| `custom.method.parse.before` | EventKey | `IGNORE_ERROR` | `METHOD` | `` | Fires before a Custom method is parsed. |
| `custom.param.as.cookie` | BooleanKey | `ANY` | `PARAMETER` | `` | Bind the parameter as a cookie when true. |
| `custom.param.as.form.body` | BooleanKey | `ANY` | `PARAMETER` | `` | Bind the parameter as a form field when true. |
| `custom.param.as.json.body` | BooleanKey | `ANY` | `PARAMETER` | `` | Bind the parameter as request body when true. |
| `custom.param.as.path.var` | BooleanKey | `ANY` | `PARAMETER` | `` | Bind the parameter as a path variable when true. |
| `custom.param.cookie` | StringKey | `SINGLE` | `PARAMETER` | `` | Cookie name (when binding=cookie). |
| `custom.param.cookie.value` | StringKey | `SINGLE` | `PARAMETER` | `` | Cookie value override. |
| `custom.param.header` | StringKey | `SINGLE` | `PARAMETER` | `` | Header name (when binding=header). |
| `custom.param.name` | StringKey | `SINGLE` | `PARAMETER` | `` | Parameter name override (query/form). |
| `custom.param.path.var` | StringKey | `SINGLE` | `PARAMETER` | `` | Path-variable name override. |
| `custom.path` | StringKey | `SINGLE` | `METHOD`/`CLASS` | `` | Base path (class) / method path (method) — context-sensitive. |

## Hoppscotch rules

| Key | Type | Mode | Context | Aliases | Summary |
|-----|------|------|---------|---------|---------|
| `hopp.class.prerequest` | StringKey | `MERGE` | `CLASS` | `class.hopp.prerequest` | Class-level Hoppscotch pre-request script (Groovy rule). |
| `hopp.class.test` | StringKey | `MERGE` | `CLASS` | `class.hopp.test` | Class-level Hoppscotch test script (Groovy rule). |
| `hopp.collection.prerequest` | EventKey | `IGNORE_ERROR` | `EMPTY` | `collection.hopp.prerequest` | Collection-level pre-request event; exposes exported endpoints. |
| `hopp.collection.test` | EventKey | `IGNORE_ERROR` | `EMPTY` | `collection.hopp.test` | Collection-level test event; exposes exported endpoints. |
| `hopp.format.after` | EventKey | `THROW_IN_ERROR` | `METHOD` | `` | Runs after one Hoppscotch endpoint is formatted. |
| `hopp.host` | StringKey | `SINGLE` | `CLASS`/`EMPTY` | `` | Base URL override for Hoppscotch endpoints. |
| `hopp.prerequest` | StringKey | `MERGE` | `METHOD`/`CLASS` | `` | Generates a Hoppscotch pre-request script (Groovy rule; the script runs in Hoppscotch). |
| `hopp.test` | StringKey | `MERGE` | `METHOD`/`CLASS` | `` | Generates a Hoppscotch test script (Groovy rule; the script runs in Hoppscotch). |

## Implicit config keys (read by name, no RuleKey constant)

| Key | Type | Mode | Context | Aliases | Summary |
|-----|------|------|---------|---------|---------|
| `markdown.curl.host` | StringKey | — | `—` | `` | Host override for the cURL placeholder in Markdown export. |
| `markdown.template.url.max.bytes` | StringKey | — | `—` | `` | Maximum bytes for a fetched remote Markdown template. |
| `markdown.template.url.ttl.seconds` | StringKey | — | `—` | `` | TTL (seconds) for the cached remote Markdown template. |
| `max.deep` | StringKey | — | `—` | `` | Maximum parse depth for object models. |
| `max.elements` | StringKey | — | `—` | `` | Maximum element count for object models. |

## OpenAPI rules

| Key | Type | Mode | Context | Aliases | Summary |
|-----|------|------|---------|---------|---------|
| `openapi.format.after` | EventKey | `THROW_IN_ERROR` | `METHOD` | `` | Runs after the OpenAPI document is built and before it is serialized. |
| `openapi.host` | StringKey | `SINGLE` | `CLASS`/`EMPTY` | `` | Base URL override for the OpenAPI servers array (legacy). |
| `openapi.info.description` | StringKey | `SINGLE` | `EMPTY` | `` | OpenAPI document info.description override. |
| `openapi.info.title` | StringKey | `SINGLE` | `EMPTY` | `` | OpenAPI document info.title override. |
| `openapi.info.version` | StringKey | `SINGLE` | `EMPTY` | `` | OpenAPI document info.version override. |
| `openapi.server.url` | StringKey | `SINGLE` | `EMPTY` | `` | Base URL override for the OpenAPI servers array. |

## Postman rules

| Key | Type | Mode | Context | Aliases | Summary |
|-----|------|------|---------|---------|---------|
| `postman.class.prerequest` | StringKey | `MERGE` | `CLASS` | `class.postman.prerequest` | Class-level pre-request script that runs BEFORE the request — inject headers, compute signatures, mutate pm.request. |
| `postman.class.test` | StringKey | `MERGE` | `CLASS` | `class.postman.test` | Class-level test script that runs AFTER the response — assert on pm.response, store tokens via pm.environment.set. |
| `postman.collection.prerequest` | EventKey | `IGNORE_ERROR` | `EMPTY` | `collection.postman.prerequest` | Collection-level pre-request event (Groovy rule; not a dashboard pm script). |
| `postman.collection.test` | EventKey | `IGNORE_ERROR` | `EMPTY` | `collection.postman.test` | Collection-level test event (Groovy rule; not a dashboard pm script). |
| `postman.format.after` | EventKey | `THROW_IN_ERROR` | `METHOD` | `` | Runs after one Postman item is created; item and endpoint are key-specific export extensions. |
| `postman.host` | StringKey | `SINGLE` | `CLASS`/`EMPTY` | `` | Base URL override for Postman requests. |
| `postman.prerequest` | StringKey | `MERGE` | `METHOD`/`CLASS` | `` | Pre-request script that runs BEFORE the request — inject headers, compute signatures, mutate pm.request. |
| `postman.test` | StringKey | `MERGE` | `METHOD`/`CLASS` | `` | Test script that runs AFTER the response — assert on pm.response, store tokens via pm.environment.set. |

> `postman.class.prerequest` notes: Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.; response is null before the request is sent.
> `postman.class.test` notes: Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.; response is available only after the request completes.
> `postman.prerequest` notes: Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.; response is null before the request is sent.
> `postman.test` notes: Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.; response is available only after the request completes.

---

## Common key-name mistakes (do NOT use)

| Does NOT exist | Use instead |
|----------------|-------------|
| `api.header` | `method.additional.header` |
| `api.header.additional` | `method.additional.header` |
| `path.prefix` | `class.prefix.path` / `endpoint.prefix.path` |
