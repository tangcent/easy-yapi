# EasyYapi Usage Guide

This guide walks through the most common EasyYapi workflows — installation, first export, the API Dashboard, search, field conversion, pre/post scripts, and AI-assisted rule creation.

> For the full rule key catalog and filter syntax, see the [Rule Authoring Guide](rule-guide.md). For settings reference, see the [Settings Guide](settings-guide.md). For Postman-compatible Groovy scripting, see the [Script Reference](easyapi-script-reference.md).

---

## Table of Contents

1. [Install](#install)
2. [First Export](#first-export)
3. [API Dashboard](#api-dashboard)
4. [Search](#search)
5. [Field Conversion](#field-conversion)
6. [Pre/Post Scripts](#prepost-scripts)
7. [AI-assisted Rule Creation](#ai-assisted-rule-creation)

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

## API Dashboard

The **API Dashboard** is a tool window (right side of the IDE) that shows a tree view of all API endpoints in your project.

### Opening the dashboard

- **View → Tool Windows → API Dashboard**, or click the dashboard icon in the right tool bar.

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

The API Dashboard includes a search bar that filters endpoints by:

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

See the [Rule Authoring Guide](rule-guide.md#json-rules) for the full list of JSON-related rule keys.

---

## Pre/Post Scripts

EasyYapi supports Postman-compatible **pre-request** and **post-response** scripts written in Groovy. These run before/after API calls initiated from the dashboard.

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

For the full `pm.*` API reference, see the [Script Reference](easyapi-script-reference.md).

---

## AI-assisted Rule Creation

EasyYapi 3.0 includes an AI assistant that can author rules for you in natural language.

### Prerequisites

1. Configure an AI provider in **Settings → EasyYapi → AI** (the dedicated AI tab).
2. Click **Test Connection** to verify.

### Workflow

1. Open **Settings → EasyYapi → Rules**.
2. Click **Chat** (bottom action bar) to reveal the inline AI panel, or **Magic** to run a built-in review-and-detect instruction.
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
