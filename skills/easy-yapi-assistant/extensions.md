# EasyApi extension points & registered implementations

Auto-generated from `src/main/resources/META-INF/plugin.xml` (`SkillFactsExporter`). Do **not** edit by hand — run `./gradlew syncSkillFacts`. Adding an extension means an `<extensionPoints>` declaration **plus** an implementation entry — this file is the full wiring overview.

## Extension points

| EP name | Interface | Area | Dynamic | Implementations |
|---------|-----------|------|---------|-----------------|
| `classExporter` | `com.itangcent.easyapi.core.export.ClassExporter` | `IDEA_PROJECT` | yes | 6 |
| `channel` | `com.itangcent.easyapi.channel.spi.Channel` | `IDEA_PROJECT` | yes | 7 |
| `fieldFormatChannel` | `com.itangcent.easyapi.format.spi.FieldFormatChannel` | _(application)_ | yes | 4 |
| `apiClassRecognizer` | `com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer` | `IDEA_PROJECT` | yes | 6 |
| `featureContributor` | `com.itangcent.easyapi.core.feature.FeatureContributor` | `IDEA_PROJECT` | yes | 2 |

## Registered implementations

### `classExporter` (6)

- `com.itangcent.easyapi.framework.springmvc.SpringMvcClassExporter`
- `com.itangcent.easyapi.framework.feign.FeignClassExporter`
- `com.itangcent.easyapi.framework.jaxrs.JaxRsClassExporter`
- `com.itangcent.easyapi.framework.springmvc.ActuatorEndpointExporter`
- `com.itangcent.easyapi.framework.grpc.GrpcClassExporter`
- `com.itangcent.easyapi.framework.custom.CustomClassExporter`

### `channel` (7)

- `com.itangcent.easyapi.channel.markdown.MarkdownChannel`
- `com.itangcent.easyapi.channel.postman.PostmanChannel`
- `com.itangcent.easyapi.channel.yapi.YapiChannel`
- `com.itangcent.easyapi.channel.curl.CurlChannel`
- `com.itangcent.easyapi.channel.httpclient.HttpClientChannel`
- `com.itangcent.easyapi.channel.hoppscotch.HoppscotchChannel`
- `com.itangcent.easyapi.channel.openapi.OpenApiChannel`

### `fieldFormatChannel` (4)

- `com.itangcent.easyapi.format.json.JsonFieldFormatChannel`
- `com.itangcent.easyapi.format.json5.Json5FieldFormatChannel`
- `com.itangcent.easyapi.format.properties.PropertiesFieldFormatChannel`
- `com.itangcent.easyapi.format.yaml.YamlFieldFormatChannel`

### `apiClassRecognizer` (6)

- `com.itangcent.easyapi.framework.springmvc.SpringControllerRecognizer`
- `com.itangcent.easyapi.framework.jaxrs.JaxRsResourceRecognizer`
- `com.itangcent.easyapi.framework.feign.FeignClientRecognizer`
- `com.itangcent.easyapi.framework.springmvc.ActuatorEndpointRecognizer`
- `com.itangcent.easyapi.framework.grpc.GrpcServiceRecognizer`
- `com.itangcent.easyapi.framework.custom.CustomApiRecognizer`

### `featureContributor` (2)

- `com.itangcent.easyapi.core.feature.CoreFeatureContributor`
- `com.itangcent.easyapi.core.feature.LegacyFeatureContributor`

> Each implementation's `id` and `enabledByDefault` are **instance** properties, and the registries that resolve them (`ChannelRegistry` / `FieldFormatChannelRegistry` / `FrameworkRegistry`) are project-scoped `@Service`s reading a project-area EP — so neither value is derivable at build time. Read them off the class above; the user's stored preference (Settings) overrides `enabledByDefault` at runtime.
