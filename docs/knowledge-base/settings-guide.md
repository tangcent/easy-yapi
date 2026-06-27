# EasyApi Settings Guide

This guide documents every setting in **Settings → EasyApi**. Settings are organized into tabs: General, Postman, YApi, HTTP, Intelligent, Extensions, Remote, Rules, AI, gRPC, and Environments. Each section below covers one tab, listing every field with its label, default value, effect, and the underlying `Settings` property name.

> **Tip:** Project-level settings override application-level settings. Use the gear icon in the settings dialog to switch scope.

---

## Table of Contents

1. [General](#general)
2. [Postman](#postman)
3. [YApi](#yapi)
4. [HTTP](#http)
5. [Intelligent](#intelligent)
6. [Extensions](#extensions)
7. [Rules](#rules)
8. [AI](#ai)
9. [gRPC](#grpc)
10. [Environments](#environments)

---

## General

Framework recognition and output formatting.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Enable Feign | `false` | Recognize Spring Cloud Feign clients as API endpoints | `feignEnable` |
| Enable JAX-RS | `true` | Recognize JAX-RS `@Path` / `@GET` / `@POST` annotations | `jaxrsEnable` |
| Enable Actuator | `false` | Recognize Spring Boot Actuator endpoints | `actuatorEnable` |
| Path Multi | `ALL` | Controls multi-path handling mode (`ALL`, `FIRST`, `LAST`) | `pathMulti` |
| Infer Return Main | `true` | Extract the inner type of wrapper types (e.g., `Mono<T>` → `T`) | `inferReturnMain` |
| Query Expanded | `true` | Expand query parameters in the exported tree | `queryExpanded` |
| Form Expanded | `true` | Expand form parameters in the exported tree | `formExpanded` |
| Output Demo | `true` | Include demo/example values in exports | `outputDemo` |
| Output Charset | `UTF-8` | Character encoding for exported files | `outputCharset` |
| Markdown Format | `SIMPLE` | Markdown export format (`SIMPLE` or `DETAILED`) | `markdownFormatType` |
| Log Level | `100` (SILENT) | Console verbosity. Lower = more verbose. `100`=SILENT, `50`=WARN, `20`=INFO, `10`=DEBUG, `0`=TRACE | `logLevel` |
| Gutter Icon | `true` | Show gutter icons for API endpoints in the editor | `gutterIconEnabled` |
| Switch Notice | `true` | Show a notification when switching settings scope | `switchNotice` |

---

## Postman

Postman collection export configuration.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Postman Token | *(empty)* | Postman API token for collection upload | `postmanToken` |
| Postman Workspace | *(empty)* | Target workspace ID for Postman uploads | `postmanWorkspace` |
| Postman Export Mode | `CREATE_NEW` | Export mode: `CREATE_NEW`, `UPDATE`, or `OVERWRITE` | `postmanExportMode` |
| Postman Collections | *(empty)* | Comma-separated collection IDs for update mode | `postmanCollections` |
| Build Example | `true` | Include example responses in the collection | `postmanBuildExample` |
| Wrap Collection | `false` | Wrap endpoints in a top-level folder | `wrapCollection` |
| Auto Merge Script | `false` | Automatically merge pre/post scripts | `autoMergeScript` |
| JSON5 Format Type | `EXAMPLE_ONLY` | JSON5 body format: `EXAMPLE_ONLY` or `SCHEMA_AND_EXAMPLE` | `postmanJson5FormatType` |

---

## YApi

YApi server export configuration.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| YApi Server | *(empty)* | YApi server URL (e.g., `http://yapi.example.com`) | `yapiServer` |
| YApi Tokens | *(empty)* | Project tokens for YApi upload (newline-separated) | `yapiTokens` |
| Enable URL Templating | `true` | Enable URL variable templating | `enableUrlTemplating` |
| YApi Export Mode | `ALWAYS_UPDATE` | Export mode: `ALWAYS_UPDATE`, `NEW_ONLY`, or `UPDATE_ONLY` | `yapiExportMode` |
| Request Body JSON5 | `false` | Use JSON5 format for request bodies | `yapiReqBodyJson5` |
| Response Body JSON5 | `false` | Use JSON5 format for response bodies | `yapiResBodyJson5` |

---

## HTTP

HTTP client behavior for API calls (YApi upload, remote config fetch, AI provider calls).

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| HTTP Timeout | `30` (seconds) | Timeout for HTTP requests | `httpTimeOut` |
| Unsafe SSL | `false` | Skip SSL certificate verification | `unsafeSsl` |
| HTTP Client | `APACHE` | HTTP client implementation: `APACHE` or `OKHTTP` | `httpClient` |

---

## Intelligent

Smart inference and auto-detection.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Auto Scan Enabled | `true` | Automatically scan the project for API endpoints | `autoScanEnabled` |
| Concurrent Scan | `false` | Use parallel scanning for faster discovery | `concurrentScanEnabled` |
| Enum Field Auto Infer | `false` | Automatically infer enum field values | `enumFieldAutoInferEnabled` |

---

## Extensions

Plugin extension codes for custom behavior.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Extension Configs | *(default codes)* | Extension configuration codes (comma-separated) | `extensionConfigs` |

---

## Rules

Rule file management. EasyApi 3.0 discovers rule files by **folder**, not by
an explicit list. The tab has three sub-tabs — **Project**,
**Global**, **Remote** — and a bottom action bar with **Chat**, **Magic**, and
**Help** buttons that host the inline AI assistant.

### Project sub-tab

Lists every regular file in `<project>/.easyapi/` (editable: add / edit /
rename / remove). Below it, a read-only list shows **legacy**
`.easy.api.config*` files discovered by walking up from the project root
(toggle Enabled only — no add / remove). The Enabled checkbox round-trips
through `Settings.disabledAutoRuleFiles`.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| `.easyapi/` files | *(empty)* | Discovered automatically from the folder. Add creates a new file; Remove deletes it from disk. | *(folder-based — no property)* |
| Disabled Auto Rule Files | *(empty)* | Array of auto-detected / `.easyapi/` file paths the user has unchecked. | `disabledAutoRuleFiles` |

### Global sub-tab

Lists every regular file in `~/.easyapi/` (editable: add / edit / rename /
remove). The Enabled checkbox round-trips through
`Settings.disabledGlobalRuleFiles`.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| `~/.easyapi/` files | *(empty)* | Discovered automatically from the folder. | *(folder-based — no property)* |
| Disabled Global Rule Files | *(empty)* | Array of global file paths the user has unchecked. | `disabledGlobalRuleFiles` |

### Remote sub-tab

Remote configuration sources (URLs that return rule/config content).

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Remote Config | *(empty)* | Array of remote config URLs | `remoteConfig` |

### Bottom action bar

- **Chat** — reveals the inline AI chat panel. Type a request in natural
  language; the assistant reads your rules, reasons, and proposes content.
- **Magic** — runs a built-in "review and improve" instruction that also asks
  the assistant to detect custom framework patterns that lack a rule.
- **Help** — opens the knowledge-base overview (`docs/knowledge-base/README.md`)
  in the editor. The file is copied to the project cache directory first.

---

## AI

Dedicated tab for AI provider configuration (rule-authoring assistant).
Formerly the **Other (AI Assistant)** tab; promoted to a top-level tab in the
3.0 rework.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| AI Provider | `OPENAI` | Provider: `OPENAI`, `ANTHROPIC`, `GEMINI`, `OLLAMA`, `AZURE_OPENAI`, or `CUSTOM` | `aiProvider` |
| Base URL | *(provider default)* | API base URL. Auto-filled when the provider changes. | `aiBaseUrl` |
| API Key | *(stored in PasswordSafe)* | API key for the provider. Stored securely in IntelliJ's `PasswordSafe`. Required for `OPENAI`, `ANTHROPIC`, `GEMINI`, `AZURE_OPENAI`. Not required for `OLLAMA`. | *(not in Settings — stored in PasswordSafe)* |
| Model | *(provider default)* | Model name (e.g., `gpt-4`, `claude-3-5-sonnet`, `llama3`) | `aiModel` |
| Request Timeout | `60` (seconds) | Per-request timeout for AI API calls | `aiRequestTimeoutSec` |
| Max Agent Steps | `8` | Maximum number of Perception-Reasoning-Action steps per turn | `aiMaxAgentSteps` |

### Test Connection button

Validates the current configuration by sending a minimal test request to the provider. The button is disabled while the test is running and shows "Testing…". Results are surfaced as a notification balloon (success or failure). The test uses the **on-screen** field values, not persisted settings — so you can verify before clicking Apply/OK.

### Auto-detect button

Attempts to auto-detect the AI provider from the environment (checks for `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `OLLAMA_HOST`, etc.).

---

## gRPC

gRPC support configuration.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Enable gRPC | `true` | Recognize gRPC service definitions | `grpcEnable` |
| gRPC Call Enabled | `false` | Enable gRPC call execution from the dashboard | `grpcCallEnabled` |
| Artifact Configs | *(empty)* | Array of protobuf artifact configuration strings | `grpcArtifactConfigs` |
| Additional Jars | *(empty)* | Array of additional JAR paths for gRPC stub resolution | `grpcAdditionalJars` |
| Repositories | *(empty)* | Array of repository URLs for artifact resolution | `grpcRepositories` |

---

## Environments

Environment variable management for export and script execution.

| Field | Default | Effect | Property |
|-------|---------|--------|----------|
| Project Environments | *(empty)* | Project-scoped environment definitions (JSON) | `projectEnvironments` |
| Global Environments | *(empty)* | Application-scoped environment definitions (JSON) | `globalEnvironments` |
