# EasyApi Rule-Key Catalog

Snapshot of every supported rule key. Source of truth:
`src/main/kotlin/com/itangcent/easyapi/rule/RuleKeys.kt` in the easy-yapi repo.

**Never invent keys not listed here** — unknown keys are silently ignored by
the plugin's config loader. In particular, `api.header` and `path.prefix` do
**not** exist (see the "Common mistakes" table at the end).

## Legend

- **Type** — `string` / `boolean` / `event` / `int`
- **Mode** — `replace` (last wins, default) / `merge` (concatenate) /
  `merge-distinct` (concatenate, dedupe) / `throw-in-error` (event mode)

## API metadata

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `api.name` | string | replace | |
| `api.tag` | string | merge-distinct | |
| `api.status` | string | replace | |
| `api.open` | boolean | replace | |
| `folder.name` | string | replace | |
| `ignore` | boolean | replace | |

## Method rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `method.doc` | string | merge-distinct | |
| `class.doc` | string | merge-distinct | |
| `method.default.http.method` | string | replace | |
| `method.content.type` | string | replace | |
| `method.return` | string | replace | |
| `method.return.main` | string | replace | |
| `class.prefix.path` | string | replace | |
| `endpoint.prefix.path` | string | replace | |
| `path.multi` | string | replace | |

## Parameter rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `param.name` | string | replace | |
| `param.type` | string | replace | |
| `param.required` | boolean | replace | |
| `param.ignore` | boolean | replace | |
| `param.default.value` | string | replace | |
| `param.doc` | string | merge-distinct | `doc.param` |
| `param.http.type` | string | replace | |
| `param.demo` | string | replace | |
| `param.mock` | string | replace | |

## Field rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `field.name` | string | replace | `json.rule.field.name` |
| `field.name.prefix` | string | replace | |
| `field.name.suffix` | string | replace | |
| `field.required` | boolean | replace | |
| `field.ignore` | boolean | replace | |
| `field.default.value` | string | replace | |
| `field.doc` | string | merge-distinct | `doc.field` |
| `field.demo` | string | replace | |
| `field.order` | string | replace | |
| `field.order.with` | string | replace | |
| `field.mock` | string | replace | |
| `field.advanced` | string | merge | |

## JSON rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `json.field.parse.before` | event | — | `field.parse.before` |
| `json.field.parse.after` | event | — | `field.parse.after` |
| `json.class.parse.before` | event | — | |
| `json.class.parse.after` | event | — | |
| `json.additional.field` | string | merge | |
| `json.rule.convert` | string | replace | |
| `json.unwrapped` | boolean | replace | |

## API lifecycle events

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `api.class.parse.before` | event | — | |
| `api.class.parse.after` | event | — | |
| `api.method.parse.before` | event | — | |
| `api.method.parse.after` | event | — | |
| `api.param.parse.before` | event | — | `param.before` |
| `api.param.parse.after` | event | — | `param.after` |
| `export.after` | event | — | |

## Additional headers / params

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `method.additional.header` | string | merge | |
| `method.additional.param` | string | merge | |
| `method.additional.response.header` | string | merge | |

> `method.additional.header` and `method.additional.param` values are **JSON
> objects** (one per line):
> `{"name":"…","value":"…","desc":"…","required":…}` — not `Name:Value`.

## HTTP call events

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `http.call.before` | event | — | |
| `http.call.after` | event | — | |

## Class recognizer rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `class.is.spring.ctrl` | boolean | replace | `class.is.ctrl` |
| `class.is.feign.ctrl` | boolean | replace | |
| `class.is.jaxrs.ctrl` | boolean | replace | |
| `class.is.quarkus.ctrl` | boolean | replace | |
| `class.is.grpc` | boolean | replace | |

## Postman rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `postman.prerequest` | string | merge | |
| `postman.class.prerequest` | string | merge | `class.postman.prerequest` |
| `postman.collection.prerequest` | event | — | `collection.postman.prerequest` |
| `postman.test` | string | merge | |
| `postman.class.test` | string | merge | `class.postman.test` |
| `postman.collection.test` | event | — | `collection.postman.test` |
| `postman.host` | string | replace | |
| `postman.format.after` | event | throw-in-error | |

## YApi rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `yapi.project` | string | replace | `project`, `module` |
| `yapi.export.before` | event | throw-in-error | |
| `yapi.save.before` | event | throw-in-error | |
| `yapi.save.after` | event | throw-in-error | |

## Enum / constant rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `enum.use.custom` | string | replace | |
| `constant.field.ignore` | boolean | replace | |

## Properties rules

| Key | Type | Mode | Aliases |
|-----|------|------|---------|
| `properties.prefix` | string | replace | |

---

## Common key-name mistakes (do NOT use)

| Does NOT exist | Use instead |
|----------------|-------------|
| `api.header` | `method.additional.header` |
| `api.header.additional` | `method.additional.header` |
| `path.prefix` | `class.prefix.path` / `endpoint.prefix.path` |
