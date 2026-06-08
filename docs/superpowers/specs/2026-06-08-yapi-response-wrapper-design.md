# Yapi Response Wrapper Design

## Goal

Add a Yapi-only export option that wraps each controller method response in a configurable response object. The main use case is exporting a response such as:

```json
{"code":0,"msg":"","data":"$response"}
```

where `"$response"` is replaced by the schema or example for the original controller return value.

## Scope

This feature applies only to Yapi export. It must not change the shared API model, response inference, Postman export, Markdown export, HTTP client export, or API Dashboard behavior.

The feature is configured in the EasyApi settings page under the existing Yapi tab.

## Settings

Add two application-level Yapi settings:

- `yapiResponseWrapperEnabled: Boolean`
- `yapiResponseWrapperTemplate: String`

The Yapi settings UI adds:

- `Enable response wrapper`
- `Response wrapper template`

When the wrapper is enabled and the template is blank, EasyYapi uses this default template:

```json
{"code":0,"msg":"","data":"$response"}
```

## Template Contract

The response wrapper template must be a JSON object.

The exact string value `"$response"` marks where the original controller return value is inserted. The field name is not special. For example, all of these are valid:

```json
{"code":0,"msg":"","data":"$response"}
```

```json
{"success":true,"message":"","result":"$response"}
```

Multiple `"$response"` values are allowed. Each occurrence is replaced with the original response schema or example.

If the template is invalid or does not contain `"$response"`, Yapi export falls back to the original response body and logs a warning. Export should continue.

## JSON Schema Output

When `Response body JSON5` is disabled, Yapi export emits JSON Schema as it does today.

The wrapper converts normal JSON template values into schema nodes:

- JSON string -> `{ "type": "string" }`
- JSON integer -> `{ "type": "integer" }`
- JSON decimal -> `{ "type": "number" }`
- JSON boolean -> `{ "type": "boolean" }`
- JSON object -> `{ "type": "object", "properties": ... }`
- JSON array -> `{ "type": "array", "items": ... }`
- JSON null -> `{ "type": "object" }`

The `"$response"` value is replaced by the existing response schema map built from the original `ObjectModel`.

The final schema keeps the root draft-04 `$schema` header already used by `JsonSchemaBuilder`.

Example template:

```json
{"code":0,"msg":"","data":"$response"}
```

Example schema output shape:

```json
{
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
    "code": { "type": "integer" },
    "msg": { "type": "string" },
    "data": {
      "type": "object",
      "properties": {
        "id": { "type": "integer" },
        "name": { "type": "string" }
      }
    }
  }
}
```

## JSON5 Output

When `Response body JSON5` is enabled, the wrapper still applies.

The same JSON template is used, but output is an example JSON5 body. The `"$response"` value is replaced with the original response JSON5 example generated from the existing `ObjectModelJsonConverter` behavior.

Example output shape:

```json5
{
  code: 0,
  msg: "",
  data: {
    id: 0,
    name: ""
  }
}
```

The template input remains strict JSON even when JSON5 response output is enabled. This keeps settings parsing predictable.

## Type Support

The wrapper must support all response shapes that the current Yapi response generation supports:

- primitive values such as `String`, `Int`, `Long`, `Boolean`
- arrays and collections
- maps
- VO/object models
- missing or unresolved response bodies

For missing or unresolved response bodies, the placeholder is replaced with an empty object schema or empty object example.

## Implementation Shape

Add a small Yapi-specific wrapper component, for example `YapiResponseWrapper`.

Responsibilities:

- resolve the effective template, including the default template
- parse the template as a JSON object
- detect whether at least one `"$response"` placeholder exists
- build a wrapped JSON Schema body
- build a wrapped JSON5 example body
- return a failure result when the template is invalid, so callers can fall back

`YapiFormatter` stays responsible for converting `ApiEndpoint` to `YapiApiDoc`. It receives the wrapper settings from `YapiExporter` and delegates only the response-body wrapping work to `YapiResponseWrapper`.

`YapiExporter` reads the new settings through `SettingBinder` and passes them to `YapiFormatter` along with the existing `yapiReqBodyJson5` and `yapiResBodyJson5` options.

## Error Handling

Yapi export must not fail only because the response wrapper template is invalid.

Fallback cases:

- template is not valid JSON
- template root is not a JSON object
- template has no `"$response"` placeholder
- wrapper conversion fails unexpectedly

In those cases, `YapiFormatter` uses the original unwrapped response body and logs a warning.

## Tests

Add unit coverage for the wrapper component:

- schema mode wraps a VO response
- schema mode wraps primitive, array, and map responses
- JSON5 mode wraps a VO response
- blank template uses the default template
- invalid template falls back
- template without `"$response"` falls back
- multiple `"$response"` placeholders are all replaced
- missing response body produces an empty object at the placeholder

Add focused `YapiFormatterTest` coverage:

- wrapper disabled keeps current output behavior
- wrapper enabled wraps `resBody` in schema mode
- wrapper enabled wraps `resBody` when `resBodyJson5 = true`

Add settings coverage:

- reset/apply/isModified includes the new Yapi settings fields
- application settings state copy, equality, and hash code include the new fields

## Verification

This repository is a Gradle project. Do not use the project `gradlew` script for any Gradle operation.

This repository is currently a single-module project, so run syntax verification from the project root with the system Gradle installation:

```bash
env JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk1.8.0_181.jdk/Contents/Home \
GRADLE_HOME=/Users/advance/Documents/work/gradle-4.10.3 \
GRADLE_OPTS=-Dorg.gradle.native=false \
gradle classes
```

Do not add `--no-daemon`.

If this work is later moved into a multi-module layout, verify only changed subprojects with `gradle :<sub-module>:classes` after confirming the exact subproject name, while keeping the same `JAVA_HOME`, `GRADLE_HOME`, and `GRADLE_OPTS` environment settings.
