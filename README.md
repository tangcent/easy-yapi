# EasyYapi

[![CI](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml/badge.svg)](https://github.com/tangcent/easy-yapi/actions/workflows/ci.yml)
[![codecov](https://codecov.io/gh/tangcent/easy-yapi/branch/master/graph/badge.svg?token=J6RUGI54XV)](https://codecov.io/gh/tangcent/easy-yapi)
[![](https://img.shields.io/jetbrains/plugin/v/12458?color=blue&label=version)](https://plugins.jetbrains.com/plugin/12458-easyyapi)
[![](https://img.shields.io/jetbrains/plugin/d/12458)](https://plugins.jetbrains.com/plugin/12458-easyyapi)

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

### Supported Frameworks

| Category | Supported |
|----------|-----------|
| Languages | Java, Kotlin, Scala (optional), Groovy (optional) |
| Web Frameworks | Spring MVC, Spring Cloud OpenFeign, JAX-RS (Quarkus / Jersey) |
| RPC | gRPC |
| Validation | javax.validation / Jakarta Validation |
| Serialization | Jackson, Gson |
| API Docs | Swagger 2 / OpenAPI 3 annotations |
| Spring Actuator | Actuator endpoints |

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

## How to Use

### Export APIs

1. Right-click on a controller file, class, or method in the editor or project view
2. Select **EasyApi → Export** (or press `Ctrl+E` on macOS / `Alt+Shift+E`)
3. Choose the target format (YApi / Postman / Markdown / cURL / HTTP Client)
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
2. Select **EasyApi → ToJson / ToJson5 / ToProperties**

## Configuration

EasyYapi uses a layered configuration system with multiple sources, processed in priority order:

| Priority | Source | Description |
|----------|--------|-------------|
| Highest | Runtime | Programmatic overrides set during execution |
| | Local file | `.easy.api.config` in the project root |
| | Extension | Plugin extension configs (Swagger, validation, etc.) |
| | Remote | Config files fetched from URLs |
| Lowest | Built-in | Default bundled configuration |

Configuration supports:

- **Property resolution** — Reference other config values with `${key}`
- **Directives** — Control parsing behavior (`#resolve`, `#ignore`, etc.)
- **Rule engine** — Groovy scripts, regex, annotation expressions, tag expressions
- **Remote configs** — Load shared configs from URLs (e.g., Swagger, javax.validation presets)

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

| JDK | IDE | Status |
|-----|-----|--------|
| 17 | 2025.2.1 | ✓ |

## Architecture

The plugin follows a layered architecture:

```mermaid
graph TB
    IDE["IDE Integration Layer<br/>(Actions, Dashboard, Line Markers, Search)"]
    Export["Export Layer<br/>(ExportOrchestrator → ClassExporter → ApiExporter)"]
    Core["Core Services<br/>(RuleEngine, ConfigReader, ApiIndex, HttpClient)"]
    PSI["PSI Analysis<br/>(TypeResolver, DocHelper, AnnotationHelper)"]

    IDE --> Export
    Export --> Core
    Core --> PSI
```

- **ClassExporter** — Extracts `ApiEndpoint` models from PSI classes (Spring MVC, JAX-RS, Feign, gRPC)
- **ApiExporter** — Converts `ApiEndpoint` models to output formats (YApi, Postman, Markdown, cURL, HTTP Client)
- **ExportOrchestrator** — Coordinates the full export pipeline from scanning to output
- **ApiIndex** — Caches discovered endpoints for fast search and dashboard access
- **RuleEngine** — Evaluates rule expressions to customize parsing behavior

## Documentation

- [Guide](https://easyyapi.github.io/guide/) — Overview and features
- [Installation](https://easyyapi.github.io/guide/installation) — Install from Marketplace or disk
- [Usage](https://easyyapi.github.io/guide/use) — Export and call APIs
- [Export to YApi](https://easyyapi.github.io/guide/export2yapi) — YApi export and settings
- [Export to Postman](https://easyyapi.github.io/guide/export2postman) — Postman export
- [Export to Markdown](https://easyyapi.github.io/guide/export2markdown) — Markdown export and templates
- [Call API](https://easyyapi.github.io/guide/call) — Send requests, API Dashboard, gRPC call

## Contributing

You can propose a feature request by opening an issue or a pull request. See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

Here is a list of contributors:

<a href="https://github.com/tangcent/easy-api/graphs/contributors">
  <img src="https://contrib.rocks/image?repo=tangcent/easy-yapi" />
</a>
