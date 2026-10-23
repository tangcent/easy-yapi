# EasyYapi Usage Guide

This guide walks through the most common EasyYapi workflows — installation, first export, the API Explorer, search, field conversion, pre/post scripts, AI-assisted rule creation, and calling EasyYapi from an MCP client.

> For the full rule key catalog and filter syntax, see the [Rule Authoring Guide](rule-guide.md). For settings reference, see the [Settings Guide](settings-guide.md). For Postman-compatible Groovy scripting, see the [Postman Script Reference](postman-script-reference.md).

---

## Table of Contents

1. [Install](#install)
2. [First Export](#first-export)
3. [API Explorer](#api-explorer)
4. [Search](#search)
5. [Field Conversion](#field-conversion)
6. [Pre/Post Scripts](#prepost-scripts)
7. [AI-assisted Rule Creation](#ai-assisted-rule-creation)
8. [Using EasyYapi from an MCP Client](#using-easyyapi-from-an-mcp-client)

---

## Install

1. Open **IntelliJ IDEA → Settings → Plugins → Marketplace**.
2. Search for **EasyYapi** (or **EasyYapi**).
3. Click **Install**, then restart the IDE.
4. After restart, open **Settings → EasyYapi** to configure the plugin.

### Manual install (from source)

```bash
git clone https://github.com/tangcent/easy-yapi.git
cd easy-yapi
./gradlew buildPlugin
# The plugin zip is in build/distributions/
# Install via Settings → Plugins → ⚙ → Install Plugin from Disk
```

---

## First Export

EasyYapi can export API endpoints to YApi, Postman, Markdown, cURL, or IntelliJ HTTP Client.

### Export to YApi

1. Configure your YApi server and tokens in **Settings → EasyYapi → YApi**.
2. Right-click a controller class (or package) in the Project view.
3. Select **EasyYapi → Export to YApi**.
4. The plugin scans the selected elements, resolves types, and uploads to YApi.
5. A notification appears when the upload completes.

### Export to Postman

1. (Optional) Configure your Postman token and workspace in **Settings → EasyYapi → Postman**.
2. Right-click a controller class and select **EasyYapi → Export to Postman**.
3. If a token is configured, the collection is uploaded directly. Otherwise, a JSON file is saved.

### Export to Markdown

1. Right-click a controller class and select **EasyYapi → Export to Markdown**.
2. Choose a destination file. The plugin writes a `.md` file with endpoint tables.

### Export to cURL

1. Right-click an endpoint method and select **EasyYapi → Export to cURL**.
2. The cURL command is copied to the clipboard (or saved to a file).

---

## API Explorer

The **API Explorer** is a tool window (bottom of the IDE) that shows a tree view of all API endpoints in your project.

### Opening the explorer

- **View → Tool Windows → API Explorer**, or click the explorer icon in the bottom tool bar.

### Browsing

- Endpoints are grouped by module → class → method.
- Click an endpoint to see its details (path, method, parameters, headers, body, response).
- Double-click to jump to the source code.

### Sending requests

- Select an endpoint, fill in the parameters, and click **Send**.
- The response appears in the bottom panel.
- Request parameters are persisted between sessions.

---

## Search

The API Explorer includes a search bar that filters endpoints by:

- **Path** — match against the URL path
- **Name** — match against the API name
- **HTTP method** — filter by GET/POST/PUT/DELETE/etc.

Type in the search bar to filter the tree in real time.

---

## Field Conversion

EasyYapi resolves field types from source code. When the source type doesn't match the API schema you want (e.g., `Mono<User>` should be documented as `User`), use **type conversion rules**.

### Example: Unwrap Reactor `Mono`

Create a rule file (Settings → Rules → add a `.rules` file) with:

```
#regex:reactor\.core\.publisher\.Mono<(.*?)>?json.rule.convert=${1}
```

This tells EasyYapi: whenever you encounter a type matching `Mono<X>`, treat it as `X` for documentation purposes.

### Example: Map `LocalDateTime` to `string`

```
#regex:java\.time\.LocalDateTime?json.rule.convert=string
```

See the [Rule Authoring Guide](rule-guide.md#rule-key-catalog) for the full list of JSON-related rule keys.

---

## Pre/Post Scripts

EasyYapi supports Postman-compatible **pre-request** and **post-response** scripts written in Groovy. These run before/after API calls initiated from the API Explorer.

### Quick example

```groovy
// Pre-request: set a timestamp header
pm.request.headers.add("X-Timestamp", System.currentTimeMillis().toString())
```

```groovy
// Post-response: assert status code
pm.test("Status is 200") {
    pm.expect(pm.response.code).to.eql(200)
}
```

### Where to define scripts

- **Per-endpoint** — use the `postman.prerequest` / `postman.test` rule keys in a `.rules` file.
- **Per-class** — use `postman.class.prerequest` / `postman.class.test`.
- **Collection-level** — use `postman.collection.prerequest` / `postman.collection.test`.

For the full `pm.*` API reference, see the [Postman Script Reference](postman-script-reference.md).

---

## AI-assisted Rule Creation

EasyYapi 3.0 includes an AI assistant that can author rules for you in natural language.

### Prerequisites

1. Configure an AI provider in **Settings → EasyYapi → AI** (the dedicated AI tab).
2. Click **Test Connection** to verify.

### Workflow

1. Open **Settings → EasyYapi → Rules**.
2. Add or edit a rule file to open the Rule File Editor dialog, then click **Chat** in its bottom bar to reveal the inline AI panel, or **Magic** to run a built-in review-and-detect instruction.
3. Type a request, e.g.:
   - "Rename all endpoints in `UserController` to start with `fetch_`"
   - "Add the tag `internal` to all classes in the `internal` package"
   - "Ignore all fields named `password` or `secret`"
4. The assistant reads your existing rules, reasons about the request, and proposes rule content.
5. Review the proposal card. You can **Edit** the content before saving.
6. Click **Save…** — choose Global (`~/.easyapi/`) or Project (`<project>/.easyapi/`) scope + filename.
7. The rule file is written into the chosen folder and the config is reloaded immediately (the folder is the source of truth — no settings-list registration needed).

### What the assistant can do

- **Read** your current rules (`list_rule_keys`, `get_existing_rules`, `read_rule_file`)
- **Read** plugin documentation (`get_plugin_doc`)
- **Propose** new rule content (`propose_rule_content`)
- **Save** to a rule file (with your approval)

The assistant **never writes files without your explicit approval** — every action tool requires an Approve click.

For the full rule key catalog, see the [Rule Authoring Guide](rule-guide.md).

---

## Using EasyYapi from an MCP Client

IntelliJ IDEA 2025.2 and later ship a built-in MCP (Model Context Protocol) server. When it is turned on, EasyYapi adds a tool to it for an external agent connected to the IDE: `easyapi_scan_endpoints` reports the endpoints EasyYapi would export for the classes, methods or source files the agent names. It is read-only: it never exports or changes anything.

Unlike the built-in AI assistant ([AI-assisted Rule Creation](#ai-assisted-rule-creation)), which works inside the IDE and writes rules for you, the tool lets an external agent drive the running EasyYapi. It describes its parameters, its result and its error codes to the client; this section covers the workflow around it.

### Setup

1. IntelliJ IDEA 2025.2 or later, with the bundled **MCP Server** plugin enabled.
2. Turn the server on in **Settings → Tools → MCP Server**, then connect your client as described in the [IntelliJ IDEA MCP Server documentation](https://www.jetbrains.com/help/idea/mcp-server.html).
3. If that page lists the tools it exposes, make sure `easyapi_scan_endpoints` is enabled.

### Scanning endpoints

The agent names the targets in `classes`, `methods` (`ClassName#methodName`) or `files` (paths relative to the project root), and the project in `projectPath` (see [Choosing the project](#choosing-the-project)). Only the targets are scanned, with the same scanner and rules as a normal export, and every target must resolve, or nothing is scanned. A target without endpoints — a request or response model, a service, an unmapped method, or a class whose export failed (see `idea.log`) — is listed in `skipped` with the reason; if you changed a model or a service, pass the controllers or methods that use it.

### Files changed outside the IDE

The tool scans what the IDE has loaded. A file changed outside the IDE, for example by the agent's own file tools, is scanned with its old content until the IDE syncs the change from disk, which it does on its own schedule; a class, method or file added outside the IDE is not found until then. So a call made right after such an edit may report the old endpoints, or fail with `TARGETS_UNRESOLVED` for a new class or method; call again once the IDE has picked up the change.

### Errors

A call that fails returns `{"error": {"code": …, "message": …}}` instead of its result; the message says what to do.

| `code` | Meaning |
|--------|---------|
| `TARGETS_REQUIRED` | No target was given. |
| `TARGETS_UNRESOLVED` | A target did not resolve; `failures` lists each one. |
| `NO_ENDPOINTS` | No target yields an endpoint; `skipped` says why. |
| `TARGETS_CHANGED` | A target changed during the scan. Retry. |
| `INDEX_NOT_READY` | The IDE is indexing. Retry when it is done. |
| `PROJECT_AMBIGUOUS` | Several projects are open and `projectPath` was not passed. |
| `PROJECT_NOT_FOUND` | No open project contains `projectPath`. |
| `INTERNAL_ERROR` | Something unexpected failed; see `idea.log`. |

### Choosing the project

Pass `projectPath` on every call: the absolute path of the project's root or of anything inside it, such as the agent's working directory. The innermost open project that contains it answers, and `project` in every successful result names it. Without `projectPath`, a call fails with `PROJECT_AMBIGUOUS` when several projects are open (a project named only in a request header or in `_meta` does not count); with one open, IntelliJ IDEA 2025.2 accepts it. Newer IDE versions (2026.2, for example) check the project before the tool runs, and reject a call they cannot place with their own error text, not an `{"error": …}` result.
