# EasyYapi

[![CI](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-yapi/branch/master/graph/badge.svg?token=J6RUGI54XV)](https://codecov.io/gh/tangcent/easy-yapi)
[![](https://img.shields.io/jetbrains/plugin/v/12458?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12458)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/tangcent/easy-yapi)

English | [中文](README_CN.md)

> **Note:** This is the v3.0 rewrite of EasyYapi. For the source code of stable v2.x releases, see the
> [`stable/v2.x.x`](https://github.com/tangcent/easy-yapi/tree/stable/v2.x.x) branch.

An IntelliJ IDEA plugin for API development — export API documentation to YApi/Postman/Markdown, send requests, and manage endpoints directly from your code.

## Features

### API Export

Export API endpoints from your source code to multiple formats:

| Format | HTTP | gRPC | Output |
|--------|------|------|--------|
| [YApi](https://easyyapi.github.io/guide/export2yapi) | ✓ | — | Upload to YApi platform with category management, mock rules, and update confirmation |
| [Postman](https://easyyapi.github.io/guide/export2postman) | ✓ | — | JSON file or direct upload to Postman workspace |
| [Markdown](https://easyyapi.github.io/guide/export2markdown) | ✓ | ✓ | .md documentation file |
| cURL | ✓ | ✓ | Executable shell command |
| HTTP Client | ✓ | ✓ | IntelliJ HTTP Client scratch file |
| **Hoppscotch** *(Beta)* | ✓ | — | JSON file or direct upload to Hoppscotch |
| **OpenAPI** *(Beta)* | ✓ | ✓ | `.json` or `.yaml` OpenAPI 3.0.3 document |

### API Dashboard

A built-in tool window that provides a tree view of all API endpoints in your project:

- Browse endpoints organized by module and class
- Search and filter endpoints by path, name, or HTTP method
- View endpoint details (parameters, headers, body, response)
- Send HTTP requests directly from the dashboard
- Navigate to source code with a single click
- Edit request parameters with auto-persistence

### Send API Requests

Call any API endpoint directly from the editor:

- Right-click a controller method → **Call** (or press `Ctrl+C` on macOS / `Alt+Shift+C`)
- The API Dashboard opens and navigates to the selected endpoint
- Edit parameters, headers, and body before sending
- View response with syntax highlighting

### API Search Everywhere

Find API endpoints from anywhere in the IDE using IntelliJ's Search Everywhere (Double Shift):

- Search by HTTP method prefix (e.g., `GET /users`)
- Search by path, endpoint name, class name, or description
- Click a result to navigate directly to the source method

### Gutter Icons

API methods are marked with a gutter icon in the editor. Click it to open the endpoint in the API Dashboard.

### Field Conversion

Convert class fields to various formats:

- **To JSON** — Standard JSON with default values
- **To JSON5** — JSON5 format with comments support
- **To Properties** — Java `.properties` format
- **To YAML** — YAML format with Spring Boot `application.yml` semantics (honors `@ConfigurationProperties` prefix)

### Supported Frameworks

| Category | Supported |
|----------|-----------|
| **Languages** | Java, Kotlin, Scala (optional), Groovy (optional) |
| **Web Frameworks** | Spring MVC, Spring Cloud OpenFeign, JAX-RS (Quarkus / Jersey) |
| **RPC** | gRPC |
| **Custom** | Rules-driven framework for any annotation convention (opt-in) |
| **Validation** | javax.validation / Jakarta Validation |
| **Serialization** | Jackson, Gson |
| **API Docs** | Swagger / OpenAPI annotations |
| **Spring Actuator** | Actuator endpoints |

#### Spring MVC

Full support for Spring MVC annotations:

- `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping`
- `@RequestParam`, `@PathVariable`, `@RequestBody`, `@RequestHeader`, `@CookieValue`
- `@RestController`, `@Controller`
- Class-level and method-level mapping composition
- Generic type resolution for parameterized controllers
- Custom meta-annotation support

#### Spring Cloud OpenFeign

Support for Feign client interfaces:

- `@FeignClient` interface detection
- Spring MVC annotations on interface methods
- Native Feign annotations: `@RequestLine`, `@Headers`, `@Body`, `@Param`

#### JAX-RS

Full support for JAX-RS annotations:

- `@Path`, `@GET`, `@POST`, `@PUT`, `@DELETE`, `@PATCH`, `@HEAD`, `@OPTIONS`
- `@PathParam`, `@QueryParam`, `@FormParam`, `@HeaderParam`, `@CookieParam`, `@MatrixParam`
- `@Consumes`, `@Produces`

#### gRPC

Support for gRPC service implementations:

- Service path extraction (`/<package>.<ServiceName>/<MethodName>`)
- Streaming type detection (unary, server-streaming, client-streaming, bidirectional)
- Request/response protobuf message type resolution
- Server reflection support
- Stub class resolution

### Custom

A rules-driven framework for any annotation convention not covered by the
built-ins (proprietary RPC-over-HTTP frameworks, non-standard annotation
sets, etc.). Every extraction decision — which classes are APIs, which
methods are endpoints, HTTP method, path, parameter binding — is delegated
to a unified `custom.*` rule surface. Disabled by default; enable it in
Settings → Framework Support and supply extraction rules in your
`.yapi.config` / rules file. This is the v3.0 replacement for the v2.x
`mdoc.class.filter` / `mdoc.method.filter` "generic export" keys (issue
#1423). See the [Custom Framework guide](docs/developer/custom-framework.md)
for the full rule surface and a Spring-equivalent reference ruleset.

## How to Use

### Export APIs

1. Right-click on a controller file, class, or method in the editor or project view
2. Select **EasyApi → Export** (or press `Ctrl+E` on macOS / `Alt+Shift+E`)
3. Choose the target format (YApi / Postman / Hoppscotch *(Beta)* / Markdown / cURL / HTTP Client / OpenAPI *(Beta)*)
4. The APIs will be exported automatically

### Call an API

1. Right-click on a controller method
2. Select **EasyApi → Call** (or press `Ctrl+C` on macOS / `Alt+Shift+C`)
3. The API Dashboard opens with the endpoint loaded
4. Edit parameters and send the request

### Open API Dashboard

- Go to **Tools → Open API Dashboard**
- Or click the **API Dashboard** tab at the bottom of the IDE

### Search APIs

1. Press **Double Shift** to open Search Everywhere
2. Switch to the **APIs** tab
3. Type an HTTP method prefix (e.g., `GET /users`) or any keyword

### Convert Fields

1. Right-click on a class in the editor
2. Select **EasyApi → ToJson / ToJson5 / ToProperties / ToYaml**

## Configuration

EasyYapi uses a layered configuration system with multiple sources, processed in priority order:

| Priority | Source | Description |
|----------|--------|-------------|
| Highest | Runtime | Programmatic overrides set during execution |
| | Project rules | `<project>/.easyapi/*.rules` (3.0 folder model) |
| | Global rules | `~/.easyapi/*.rules` (applied to every project on the machine) |
| | Legacy project file | `.easy.api.config*` in the project root (and ancestor dirs) |
| | Extension | Plugin extension configs (Swagger, validation, etc.) |
| | Remote | Config files fetched from URLs |
| Lowest | Built-in | Default bundled configuration |

Configuration supports:

- **Property resolution** — Reference other config values with `${key}`
- **Directives** — Control parsing behavior (`#resolve`, `#ignore`, etc.)
- **Rule engine** — Groovy scripts, regex, annotation expressions, tag expressions
- **Remote configs** — Load shared configs from URLs (e.g., Swagger, javax.validation presets)

### When do you need a custom rule?

EasyApi understands standard HTTP frameworks (Spring MVC, WebFlux, JAX-RS, Feign) out of the box — **most projects need no custom rules**. For custom framework behaviour the scanner can't see (e.g. a `jakarta.servlet.Filter` that requires a header, or a `ResponseBodyAdvice` that wraps every response in an envelope), use the built-in AI Assistant or the external skill to detect it and generate the rule. See the [Rule Authoring Guide](src/main/resources/docs/knowledge-base/rule-guide.md) for the full Custom-Pattern Catalog.

## Skills

EasyYapi ships an external skill that lets your favourite AI coding assistant (Trae, Cursor, Cline, Continue, etc.) author EasyApi rule files using the same knowledge base as the built-in assistant.

### Install the skill

```bash
npx skills add tangcent/easy-yapi -g -y
```

This installs the [`easy-yapi-assistant`](skills/easy-yapi-assistant/SKILL.md) skill globally. Once installed, your AI assistant will automatically invoke it when you ask to add or modify EasyApi rules.

### Two approaches to AI-assisted rule authoring

| Approach | Where it runs | Best for |
|----------|---------------|----------|
| **Built-in Rules-tab Chat / Magic** | Inside IntelliJ (Settings → EasyApi → Rules → Chat / Magic) | Users who want everything inside IntelliJ; the agent can call PSI tools to inspect the project. |
| **External skill** | Any AI coding assistant with file access | Users already invested in an external AI workflow; the assistant uses its own file/PSI access. |

The built-in assistant reads [`docs/knowledge-base/rule-guide.md`](src/main/resources/docs/knowledge-base/rule-guide.md) from the plugin, while the external skill bundles its own copy (`skills/easy-yapi-assistant/rule-guide.md`) — the repo file isn't available after `npx skills add`, which publishes only the `skills/easy-yapi-assistant/` folder. The two copies are kept in sync, so both approaches produce consistent rule content.

## Development

### Prerequisites

- JDK 17 or higher
- IntelliJ IDEA 2025.2 or higher

### Build & Run

```bash
# Run an IDEA instance with the plugin installed
./gradlew runIde

# Run all tests
./gradlew clean test

# Generate JaCoCo coverage report
./gradlew jacocoTestReport
```

### Compatibility

Only the default build is published to the JetBrains Marketplace.

| Build | IDE compatibility | Support level |
|-------|-------------------|---------------|
| Default (Marketplace) | IntelliJ IDEA 2025.2+ (JDK 17+) | Officially supported |
| Compatibility build | IntelliJ IDEA 2022.1 – 2025.1 | Best-effort, not verified |

If you are on an older IDEA version:

- **Download the compatibility build** — every [GitHub Release](https://github.com/tangcent/easy-yapi/releases) additionally attaches a best-effort build covering IDEA 2022.1 through 2025.1; the release notes' Downloads table lists the exact IDE range of each zip. It is compiled against the newest platform, so it is not verified on older IDEs — if it fails on your IDE, file an issue or build your own.
- **Build your own** — `script/package.sh` accepts an IDEA compatibility range as its first argument (quote ranges containing `*` so the shell does not glob-expand them):

```bash
./script/package.sh              # default: 2025.2+, unbounded (what ships to Marketplace)
./script/package.sh '221-*'       # 2022.1 and newer, unbounded
./script/package.sh 221-251      # 2022.1 through 2025.1
./script/package.sh 233-242       # any range; "*" means unbounded
```

Compatibility outside the default range is not guaranteed — the plugin may use APIs that do not exist in older IDEs; such issues are outside the project's support scope.

## Architecture

The plugin follows a layered, extension-point-driven architecture:

```mermaid
graph TB
    IDE["IDE Integration Layer<br/>(Actions, Dashboard, Line Markers, Search, AI Assistant)"]
    Export["Export Layer<br/>(ExportOrchestrator → ClassExporter EP + Channel EP)"]
    Core["Core Services<br/>(RuleEngine, ConfigReader, ApiIndex, HttpClient)"]
    PSI["PSI Analysis<br/>(TypeResolver, DocHelper, AnnotationHelper)"]

    IDE --> Export
    Export --> Core
    Core --> PSI
```

- **ExportOrchestrator** — Coordinates the full export pipeline: scans endpoints via `ApiScanner`, then hands them to the selected `Channel` for output
- **ClassExporter** *(extension point)* — Extracts `ApiEndpoint` models from PSI classes; built-in implementations: Spring MVC, Spring Cloud OpenFeign, JAX-RS, Spring Actuator, gRPC, Custom (rules-driven)
- **Channel** *(extension point)* — Converts `ApiEndpoint` models to an output format and handles file write / remote upload; built-in channels: YApi, Postman, Markdown, cURL, HTTP Client, Hoppscotch *(Beta)*, OpenAPI *(Beta)*. Adding a new output target only requires implementing `Channel` — no core edits
- **ApiIndex** — Caches discovered endpoints for fast search and dashboard access
- **RuleEngine** — Evaluates rule expressions (Groovy, regex, annotation, tag) to customize parsing behavior
- **AI Assistant** — Optional built-in agent that inspects the project via PSI tools and authors rule files; see the [Skills](#skills) section for the external-skill equivalent

### Project Structure

The plugin's source tree is organized into four top-level buckets under `src/main/kotlin/com/itangcent/easyapi/`:

```
com.itangcent.easyapi/
├── channel/      # OUTPUT — export destinations (YApi, Postman, Markdown, cURL, Hoppscotch, HTTP Client, OpenAPI)
│   ├── spi/      #   Channel EP contract: Channel, ChannelConfig, ChannelRegistry, …
│   ├── curl/
│   ├── hoppscotch/  (+ model/)
│   ├── httpclient/
│   ├── markdown/    (+ template/)
│   ├── openapi/
│   ├── postman/     (+ model/)
│   └── yapi/        (+ markdown/)  # easy-yapi only
│
├── format/       # FIELD/OBJECT SERIALIZATION (JSON, JSON5, YAML, Properties)
│   ├── spi/      #   FieldFormatChannel EP + FieldFormatExtensions (toJson/toJson5/toYaml/toProperties)
│   ├── json/
│   ├── json5/
│   ├── yaml/
│   └── properties/
│
├── framework/    # INPUT — source framework exporters (Spring MVC, JAX-RS, Feign, gRPC, Custom)
│   ├── springmvc/   # Spring MVC + Actuator
│   ├── jaxrs/
│   ├── feign/
│   ├── grpc/        # class exporter only — runtime plumbing is core/grpc
│   └── custom/      # rules-driven framework (custom.* rule surface)
│
└── core/         # SHARED INFRASTRUCTURE (the umbrella)
    ├── internal/    # relocated narrow core/ (EasyApiApplicationService, EasyApiProjectService, event/, threading/)
    ├── export/      # the neutral pipeline: ClassExporter, ClassExporterRegistry, EndpointBuilder, ExportOrchestrator, ExportContext, …  (+ recognizer/)
    ├── psi/         # (+ adapter/ doc/ helper/ model/ type/) — ObjectModel lives here; format/ consumes it
    ├── config/      # (+ model/ parser/ resource/ source/)
    ├── rule/        # (+ context/ engine/ parser/)
    ├── http/        # HttpClientProvider + Apache/IntelliJ/UrlConnection implementations
    ├── logging/     # IdeaConsole, IdeaLog, IdeaConsoleProvider
    ├── ide/         # (+ action/ dialog/ linemarker/ script/ search/ support/) — NO fieldformat/ (moved to format/)
    ├── dashboard/   # API Dashboard tool window
    ├── script/      # (+ env/ pm/) — script execution support
    ├── util/        # (+ file/ ide/ json/ storage/ text/) — FormatterHelper stays here (Decision F1)
    ├── cache/       # (+ api/ http/ json/)
    ├── settings/    # (+ migration/ module/ state/ ui/)
    ├── ai/          # (+ agent/ credentials/ tools/ ui/)
    ├── grpc/        # runtime plumbing (descriptor reflection, proto) — peer of framework/grpc's consumer
    ├── repository/
    └── extension/
```

The four buckets form a directed-acyclic dependency graph: `channel` may import from `format`, `framework`, and `core`; `format` and `framework` may import from `core`; `core` imports only extension-point contract seams (`channel.spi.*`, `format.spi.*`, `core.export.*`) from its siblings — concrete per-id implementations (`channel.<id>.*`, `format.<id>.*`, `framework.<id>.*`) imported from `core.*` are forbidden. The original narrow `core/` package (services, events, threading) was renamed to `core/internal/` so `core/` could serve as the umbrella for all shared infrastructure.

### How to add new support

The plugin is built around three IntelliJ extension points (EPs). Adding support for a new output target, field format, or source framework is a one-package operation plus one `plugin.xml` line. **Step-by-step guides** for each — SPI reference, optional config/settings/rule-keys, worked examples, import rules, and testing — live in [`docs/developer/`](docs/developer/README.md).

#### Adding a new channel (output destination)

A channel converts `ApiEndpoint` models into a specific output format and handles file write or remote upload (e.g. a Postman variant, or a new target like Insomnia). Create a `channel/<id>/` package with a `Channel` implementation and register it against the `channel` EP:

```xml
<channel implementation="com.itangcent.easyapi.channel.<id>.<ChannelClass>" />
```

→ Full guide: [docs/developer/channels.md](docs/developer/channels.md) (SPI surface, options panel, settings tab, rule keys, `handleResult`, worked example, import rules).

#### Adding a new format (field serialization)

A format serializes an `ObjectModel` to a specific representation (e.g. TOML, XML). Create a `format/<id>/` package with a pure renderer plus a `FieldFormatChannel` implementation, and register it against the `fieldFormatChannel` EP:

```xml
<fieldFormatChannel implementation="com.itangcent.easyapi.format.<id>.<FieldFormatChannelClass>" />
```

→ Full guide: [docs/developer/formats.md](docs/developer/formats.md) (two-layer architecture, `ObjectModel` input, cycle safety, entry extension, worked example, import rules).

#### Adding a new framework recognizer (source framework)

A framework scans PSI for endpoints declared with a specific framework's annotations (e.g. Micronaut). Create a `framework/<id>/` package with a `ClassExporter` and an `ApiClassRecognizer`, and register them against **both** EPs (the recognizer drives line markers, index scanning, AI discovery, and enablement — registering only the exporter silently breaks them):

```xml
<classExporter implementation="com.itangcent.easyapi.framework.<id>.<ClassExporterClass>" />
<apiClassRecognizer implementation="com.itangcent.easyapi.framework.<id>.<RecognizerClass>" />
```

→ Full guide: [docs/developer/frameworks.md](docs/developer/frameworks.md) (two-EP overview, the two SPIs, 4-step guide, rule lifecycle hooks, `ApiEndpoint` shape, worked example, import rules).

## Documentation

- [Guide](https://easyyapi.github.io/guide/) — Overview and features
- [Installation](https://easyyapi.github.io/guide/installation) — Install from Marketplace or disk
- [Usage](https://easyyapi.github.io/guide/use) — Export and call APIs
- [Export to YApi](https://easyyapi.github.io/guide/export2yapi) — YApi export and settings
- [Export to Postman](https://easyyapi.github.io/guide/export2postman) — Postman export
- [Export to Markdown](https://easyyapi.github.io/guide/export2markdown) — Markdown export and templates
- [Call API](https://easyyapi.github.io/guide/call) — Send requests, API Dashboard, gRPC call
