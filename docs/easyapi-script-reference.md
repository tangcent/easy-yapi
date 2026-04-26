# EasyAPI Script Reference — Postman-Compatible Groovy API

This document describes the EasyAPI Groovy scripting API for **Pre-request** and **Post-response** scripts in the API Dashboard. The API is designed to be structurally compatible with Postman's `pm.*` JavaScript API, so users familiar with Postman can quickly adapt. All scripts are written in **Groovy**.

> **Goal:** A user who knows Postman scripting should be able to write the equivalent Groovy script with minimal mental translation. An AI assistant can use this document to automatically convert Postman JavaScript scripts to EasyAPI Groovy scripts.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [The `pm` Object](#the-pm-object)
3. [Variable Scopes](#variable-scopes)
4. [Request Manipulation (Pre-request)](#request-manipulation-pre-request)
5. [Response Access (Post-response)](#response-access-post-response)
6. [Test Assertions](#test-assertions)
7. [Cookies](#cookies)
8. [Sending Additional Requests](#sending-additional-requests)
9. [Script Metadata](#script-metadata)
10. [Environment Model](#environment-model)
11. [Postman → EasyAPI Conversion Guide](#postman--easyapi-conversion-guide)
12. [Full API Reference](#full-api-reference)

---

## Quick Start

### Pre-request Script (runs before the request is sent)

```groovy
// Set an environment variable
pm.environment.set("timestamp", System.currentTimeMillis().toString())

// Add a header
pm.request.headers.add("X-Request-Id", UUID.randomUUID().toString())

// Upsert a header (add or update)
pm.request.headers.upsert("Authorization", "Bearer " + pm.environment.get("token"))
```

### Post-response Script (runs after the response is received)

```groovy
// Parse the response body as JSON
def json = pm.response.json()

// Test the status code
pm.test("Status is 200") {
    pm.expect(pm.response.code).to.eql(200)
}

// Test a response field
pm.test("Response has user name") {
    pm.expect(json.name).to.eql("Alice")
}

// Extract a token from the response and save it
pm.environment.set("token", json.token)
```

---

## The `pm` Object

The `pm` object is the top-level entry point for all scripting APIs, mirroring Postman's `pm` object.

| Property | Type | Pre-request | Post-response | Description |
|----------|------|:-----------:|:-------------:|-------------|
| `pm.request` | `PmRequest` | ✅ read/write | ✅ read-only | The current request |
| `pm.response` | `PmResponse` | ❌ | ✅ read-only | The current response |
| `pm.environment` | `PmVariableScope` | ✅ | ✅ | Active environment variables |
| `pm.globals` | `PmVariableScope` | ✅ | ✅ | Global variables (cross-project) |
| `pm.collectionVariables` | `PmVariableScope` | ✅ | ✅ | Project-level variables |
| `pm.variables` | `PmVariableScope` | ✅ | ✅ | Narrowest-scope variable access |
| `pm.cookies` | `PmCookies` | ❌ | ✅ | Cookie access for the request domain |
| `pm.test` | `PmTest` | ✅ | ✅ | Test assertion framework |
| `pm.expect` | `PmExpect` | ✅ | ✅ | Chai-style assertion builder |
| `pm.sendRequest` | `PmSendRequest` | ✅ | ✅ | Send additional HTTP requests |
| `pm.info` | `PmInfo` | ✅ | ✅ | Script execution metadata |

---

## Variable Scopes

EasyAPI supports multiple variable scopes, following the same precedence as Postman:

**Precedence (narrowest wins):** `local (pm.variables)` > `environment` > `collectionVariables` > `globals`

| Scope | Postman Equivalent | EasyAPI Storage | Description |
|-------|-------------------|-----------------|-------------|
| `pm.globals` | Global variables | Application-level settings | Shared across all projects |
| `pm.collectionVariables` | Collection variables | Project-level settings | Scoped to the current project |
| `pm.environment` | Environment variables | Active environment (global or project) | The currently selected environment |
| `pm.variables` | Narrowest scope | Composite resolver across all scopes | `get()` resolves from narrowest scope (local → environment → collection → global); `set()` creates a local variable |

### PmVariableScope API

All variable scope objects (`pm.environment`, `pm.globals`, `pm.collectionVariables`, `pm.variables`) share the same API:

```groovy
// Check existence
pm.environment.has("token")           // → true/false

// Get a value
pm.environment.get("token")           // → "abc123" or null

// Set a value
pm.environment.set("token", "abc123")

// Get all variables as a Map
pm.environment.toObject()             // → ["token": "abc123", "host": "api.example.com"]

// Remove a variable
pm.environment.unset("token")

// Clear all variables in this scope
pm.environment.clear()

// Resolve dynamic variables in a string ({{$randomInt}}, {{$timestamp}}, etc.)
pm.environment.replaceIn("User-{{$randomInt}}")
```

### Dynamic Variables

EasyAPI supports Postman-style dynamic variables using `{{$variableName}}` syntax. These are **automatically resolved** in URLs, headers, query parameters, and request bodies before the request is sent — just like Postman.

You can also resolve them manually in scripts using `replaceIn`:

```groovy
def resolved = pm.environment.replaceIn("User-{{$randomInt}}")
```

| Variable | Description | Example Output |
|----------|-------------|----------------|
| `{{$timestamp}}` | Current Unix timestamp | `1695847293` |
| `{{$randomInt}}` | Random integer 0–999 | `742` |
| `{{$guid}}` | UUID v4 | `a3e1f2b4-...` |
| `{{$randomAlphaNumeric}}` | 8-char alphanumeric | `aB3k9Xz2` |
| `{{$randomFirstName}}` | Random first name | `Alice` |
| `{{$randomLastName}}` | Random last name | `Smith` |
| `{{$randomEmail}}` | Random email | `user@example.com` |
| `{{$randomUrl}}` | Random URL | `http://example.com` |
| `{{$randomIP}}` | Random IPv4 | `192.168.1.1` |
| `{{$randomUuid}}` | UUID | `b14ec7f5-...` |

> **Auto-resolution example:** If you set the URL to `https://api.example.com?ts={{$timestamp}}&rid={{$randomInt}}`, EasyAPI automatically resolves `{{$timestamp}}` and `{{$randomInt}}` before sending the request.

---

## Request Manipulation (Pre-request)

The `pm.request` object allows you to inspect and modify the request before it is sent.

### pm.request Properties

```groovy
pm.request.url           // → "https://api.example.com/users"  (String)
pm.request.method        // → "GET" | "POST" | ...             (String)
pm.request.headers       // → PmHeaderList
pm.request.body          // → PmRequestBody (read-only in pre-request)
pm.request.auth          // → PmAuthConfig
```

### pm.request.headers (PmHeaderList)

```groovy
// Add a header (appends even if one with same key exists)
pm.request.headers.add("X-Custom", "value")

// Add a header as a map (Postman-compatible syntax)
pm.request.headers.add([key: "X-Custom", value: "value"])

// Upsert a header (add if missing, update if exists)
pm.request.headers.upsert("Authorization", "Bearer token123")

// Remove a header by key
pm.request.headers.remove("X-Custom")

// Get a header value
pm.request.headers.get("Content-Type")   // → "application/json" or null

// Check if a header exists
pm.request.headers.has("Authorization")  // → true/false

// Get all headers as a list of [key, value] pairs
pm.request.headers.all()                 // → [[key:"Accept", value:"*/*"], ...]
```

### pm.request.body (PmRequestBody)

The request body can be read and modified in pre-request scripts:

```groovy
// Read the raw body
def body = pm.request.body.raw

// Modify the raw body (replaces the entire body)
pm.request.body.raw = '{"name": "modified"}'

// Set the body mode
pm.request.body.mode = "raw"  // "raw", "urlencoded", or "formdata"

// For URL-encoded form data
pm.request.body.urlencoded  // → PmPropertyList

// For multipart form data
pm.request.body.formdata    // → PmPropertyList
```

### pm.request.auth (PmAuthConfig)

```groovy
// Set API Key auth
pm.request.auth.apiKey("X-API-Key", "your-api-key", "header")

// Set Bearer Token auth
pm.request.auth.bearer("your-token")

// Set Basic auth
pm.request.auth.basic("username", "password")
```

---

## Response Access (Post-response)

The `pm.response` object provides access to the response data. It is **only available in Post-response scripts**.

### pm.response Properties

```groovy
pm.response.code          // → 200                           (int)
pm.response.status        // → "OK"                          (String)
pm.response.headers       // → PmHeaderList
pm.response.responseTime  // → 342                           (long, milliseconds)
pm.response.responseSize  // → 1234                          (long, bytes)
```

### pm.response Methods

```groovy
// Get response body as raw text
pm.response.text()        // → '{"id":1,"name":"Alice"}'

// Parse response body as JSON (returns a Groovy Map/List)
pm.response.json()        // → [id:1, name:"Alice"]

// Parse response body as XML
pm.response.xml()         // → groovy.util.Node
```

### pm.response BDD Assertions

```groovy
// Status code assertions
pm.response.to.have.status(200)
pm.response.to.not.have.status(404)

// Body assertions
pm.response.to.have.body("exact body text")
pm.response.to.not.have.body("error")

// JSON body property assertions
pm.response.to.have.jsonBody("name")                    // property exists
pm.response.to.have.jsonBody("name", "Alice")           // property equals value

// Header assertions
pm.response.to.have.header("Content-Type")
pm.response.to.not.have.header("X-Debug")

// Type checks
pm.response.to.be.ok           // status 2xx
pm.response.to.be.json         // Content-Type contains json
pm.response.to.be.html         // Content-Type contains html
pm.response.to.be.xml          // Content-Type contains xml

// Negation
pm.response.to.not.be.error    // status is not 4xx/5xx
```

---

## Test Assertions

### pm.test(testName, closure)

```groovy
// Basic test
pm.test("Status code is 200") {
    pm.expect(pm.response.code).to.eql(200)
}

// Test with multiple assertions (all must pass)
pm.test("Response has valid user data") {
    def json = pm.response.json()
    pm.expect(json.name).to.be.a("String")
    pm.expect(json.age).to.be.above(0)
    pm.expect(json.id).to.exist
}

// Skip a test
pm.test.skip("Not implemented yet") {
    pm.expect(true).to.be.ok
}
```

### pm.expect(value) — Chai-style BDD Assertions

The `pm.expect` API follows Chai.js BDD syntax, adapted for Groovy:

#### Chainable Language (no-op, for readability)

`.to`, `.be`, `.been`, `.is`, `.that`, `.which`, `.and`, `.has`, `.have`, `.with`, `.at`, `.of`, `.same`, `.but`, `.does`

These can be chained freely for readability:
```groovy
pm.expect(json.name).to.be.a("String")
pm.expect(json.age).to.not.be.below(18)
```

#### Assertion Methods

| Method | Postman JS | EasyAPI Groovy | Description |
|--------|-----------|----------------|-------------|
| eql | `pm.expect(x).to.eql(y)` | `pm.expect(x).to.eql(y)` | Deep equality |
| equal | `pm.expect(x).to.equal(y)` | `pm.expect(x).to.equal(y)` | Strict equality |
| above | `pm.expect(x).to.be.above(y)` | `pm.expect(x).to.be.above(y)` | x > y |
| below | `pm.expect(x).to.be.below(y)` | `pm.expect(x).to.be.below(y)` | x < y |
| atLeast | `pm.expect(x).to.be.at.least(y)` | `pm.expect(x).to.be.atLeast(y)` | x >= y |
| atMost | `pm.expect(x).to.be.at.most(y)` | `pm.expect(x).to.be.atMost(y)` | x <= y |
| within | `pm.expect(x).to.be.within(a, b)` | `pm.expect(x).to.be.within(a, b)` | a <= x <= b |
| a / an | `pm.expect(x).to.be.a("string")` | `pm.expect(x).to.be.a("String")` | Type check |
| include | `pm.expect(s).to.include("sub")` | `pm.expect(s).to.include("sub")` | Contains substring/collection item |
| contain | `pm.expect(s).to.contain("sub")` | `pm.expect(s).to.contain("sub")` | Alias for include |
| match | `pm.expect(s).to.match(/regex/)` | `pm.expect(s).to.match(~/regex/)` | Regex match |
| lengthOf | `pm.expect(s).to.have.lengthOf(5)` | `pm.expect(s).to.have.lengthOf(5)` | Length check |
| exist | `pm.expect(x).to.exist` | `pm.expect(x).to.exist` | Not null |
| ok | `pm.expect(x).to.be.ok` | `pm.expect(x).to.be.ok` | Truthy |
| true | `pm.expect(x).to.be.true` | `pm.expect(x).to.be.isTrue()` | x == true |
| false | `pm.expect(x).to.be.false` | `pm.expect(x).to.be.isFalse()` | x == false |
| null | `pm.expect(x).to.be.null` | `pm.expect(x).to.be.isNull()` | x == null |
| empty | `pm.expect(x).to.be.empty` | `pm.expect(x).to.be.empty` | Empty string/collection/map |
| oneOf | `pm.expect(x).to.be.oneOf([a,b])` | `pm.expect(x).to.be.oneOf([a,b])` | x in list |

#### Negation

Use `.not` before the assertion:
```groovy
pm.expect(pm.response.code).to.not.eql(404)
pm.expect(json.error).to.not.exist
pm.expect(s).to.not.include("error")
```

> **Groovy keyword note:** `true`, `false`, and `null` are reserved keywords in Groovy and cannot be used as property names. Use the method forms instead: `isTrue()`, `isFalse()`, `isNull()`.
> ```groovy
> // ❌ Won't compile in Groovy — keywords can't be property names
> pm.expect(x).to.be.true
>
> // ✅ Use method form instead
> pm.expect(x).to.be.isTrue()
> pm.expect(x).to.not.be.isFalse()
> pm.expect(x).to.be.isNull()
> ```

#### Type Names for `.a()` / `.an()`

| Postman JS type | EasyAPI Groovy type |
|----------------|---------------------|
| `"string"` | `"String"` |
| `"number"` | `"Number"` |
| `"boolean"` | `"Boolean"` |
| `"object"` | `"Map"` |
| `"array"` | `"List"` |
| `"function"` | `"Closure"` |
| `"null"` | `"null"` |
| `"undefined"` | `"null"` |

---

## Cookies

```groovy
// Check if a cookie exists
pm.cookies.has("sessionId")           // → true/false

// Get a cookie value
pm.cookies.get("sessionId")           // → "abc123"

// Get all cookies as a Map
pm.cookies.toObject()                 // → ["sessionId": "abc123", ...]
```

---

## Sending Additional Requests

```groovy
// Simple GET request
pm.sendRequest("https://api.example.com/health") { response ->
    pm.expect(response.code).to.eql(200)
}

// Full request with options
pm.sendRequest([
    url: "https://api.example.com/token",
    method: "POST",
    header: [[key: "Content-Type", value: "application/json"]],
    body: [mode: "raw", raw: '{"grant_type":"client_credentials"}']
]) { response ->
    def json = response.json()
    pm.environment.set("token", json.access_token)
}
```

---

## Script Metadata

```groovy
pm.info.eventName       // → "prerequest" or "test"
pm.info.requestName     // → "Get User by ID"
pm.info.requestId       // → unique request identifier
```

---

## Environment Model

### Environment Scope

Environments can be stored at two levels:

| Scope | Storage | Visibility | Use Case |
|-------|---------|------------|----------|
| **Global** | Application-level settings | All projects | Shared credentials, common tokens |
| **Project** | Project-level settings | Current project only | Project-specific hosts, feature flags |

### Environment Data Model

```groovy
// An environment has a name, scope, and variables
[
    name: "Production",
    scope: "project",       // "global" or "project"
    variables: [
        host: "https://api.prod.example.com",
        token: "prod-token-xxx",
        timeout: "5000"
    ]
]
```

### Variable Resolution Order

When resolving `{{variableName}}` in request URLs, headers, or body:

1. **Path parameters** — `{id}` in URL path
2. **Local variables** — Set by `pm.variables.set()` in scripts
3. **Environment variables** — Active environment
4. **Project variables** — `pm.collectionVariables`
5. **Global variables** — `pm.globals`
6. **Config variables** — From `config` rules

---

## Postman → EasyAPI Conversion Guide

### Syntax Differences at a Glance

| Aspect | Postman (JavaScript) | EasyAPI (Groovy) |
|--------|---------------------|-------------------|
| Language | JavaScript | Groovy |
| String | `'single'` or `"double"` | `"double"` or `'single'` |
| Variable | `const x = 1`, `let x = 1` | `def x = 1` |
| Arrow function | `(x) => x + 1` | `{ x -> x + 1 }` |
| Object literal | `{ key: value }` | `[key: value]` |
| Array literal | `[1, 2, 3]` | `[1, 2, 3]` (same) |
| Regex literal | `/pattern/flags` | `~/pattern/` |
| Optional semicolons | ✅ | ✅ |
| Null check | `x === null` | `x == null` or `x == null` |
| String interpolation | `` `Hello ${name}` `` | `"Hello ${name}"` |
| Method calls | `pm.response.json()` | `pm.response.json()` (same) |
| Property access | `obj.prop` | `obj.prop` or `obj['prop']` |

### Conversion Examples

#### Example 1: Set Authorization Header

**Postman (JavaScript):**
```javascript
pm.request.headers.add({
    key: "Authorization",
    value: "Bearer " + pm.environment.get("token")
});
```

**EasyAPI (Groovy):**
```groovy
pm.request.headers.add([
    key: "Authorization",
    value: "Bearer " + pm.environment.get("token")
])
```

> **Difference:** Object literal `{}` → Map literal `[]`

---

#### Example 2: Parse Response and Test

**Postman (JavaScript):**
```javascript
const responseJson = pm.response.json();

pm.test("Status code is 200", function () {
    pm.response.to.have.status(200);
});

pm.test("User name is Alice", function () {
    pm.expect(responseJson.name).to.eql("Alice");
});
```

**EasyAPI (Groovy):**
```groovy
def responseJson = pm.response.json()

pm.test("Status code is 200") {
    pm.response.to.have.status(200)
}

pm.test("User name is Alice") {
    pm.expect(responseJson.name).to.eql("Alice")
}
```

> **Differences:**
> - `const` → `def`
> - `function () {` → `{`
> - Semicolons optional (Groovy convention: omit)

---

#### Example 3: Dynamic Timestamp and Nonce

**Postman (JavaScript):**
```javascript
const timestamp = Math.floor(Date.now() / 1000);
const nonce = Math.random().toString(36).substring(2);

pm.environment.set("timestamp", timestamp);
pm.environment.set("nonce", nonce);

pm.request.headers.add({
    key: "X-Timestamp",
    value: timestamp.toString()
});
pm.request.headers.add({
    key: "X-Nonce",
    value: nonce
});
```

**EasyAPI (Groovy):**
```groovy
def timestamp = System.currentTimeMillis() / 1000
def nonce = UUID.randomUUID().toString().take(8)

pm.environment.set("timestamp", timestamp as String)
pm.environment.set("nonce", nonce)

pm.request.headers.add("X-Timestamp", timestamp as String)
pm.request.headers.add("X-Nonce", nonce)
```

> **Differences:**
> - `Date.now()` → `System.currentTimeMillis()`
> - `Math.random().toString(36).substring(2)` → `UUID.randomUUID().toString().take(8)`
> - Groovy allows simplified `add(key, value)` instead of `add([key:..., value:...])`

---

#### Example 4: Extract Token and Chain Requests

**Postman (JavaScript):**
```javascript
const responseJson = pm.response.json();
pm.environment.set("auth_token", responseJson.access_token);

pm.sendRequest({
    url: pm.environment.get("host") + "/api/user/me",
    method: "GET",
    header: {
        "Authorization": "Bearer " + responseJson.access_token
    }
}, function (err, response) {
    pm.test("User profile retrieved", function () {
        pm.expect(response.code).to.eql(200);
    });
});
```

**EasyAPI (Groovy):**
```groovy
def responseJson = pm.response.json()
pm.environment.set("auth_token", responseJson.access_token)

pm.sendRequest([
    url: pm.environment.get("host") + "/api/user/me",
    method: "GET",
    header: [[key: "Authorization", value: "Bearer " + responseJson.access_token]]
]) { response ->
    pm.test("User profile retrieved") {
        pm.expect(response.code).to.eql(200)
    }
}
```

> **Differences:**
> - Header format: `{ "Key": "Value" }` → `[[key: "Key", value: "Value"]]`
> - Callback: `function (err, response) {` → `{ response ->`
> - No `err` parameter (exceptions are handled by the framework)

---

#### Example 5: JSON Schema Validation

**Postman (JavaScript):**
```javascript
const schema = {
    "type": "object",
    "properties": {
        "id": { "type": "integer" },
        "name": { "type": "string" }
    },
    "required": ["id", "name"]
};

pm.test("Schema is valid", function () {
    pm.response.to.have.jsonSchema(schema);
});
```

**EasyAPI (Groovy):**
```groovy
def schema = [
    type: "object",
    properties: [
        id  : [type: "integer"],
        name: [type: "string"]
    ],
    required: ["id", "name"]
]

pm.test("Schema is valid") {
    pm.response.to.have.jsonSchema(schema)
}
```

> **Difference:** JSON object → Groovy Map literal (no quotes needed for keys)

---

#### Example 6: Common Test Patterns

**Postman (JavaScript):**
```javascript
pm.test("Response time is less than 200ms", function () {
    pm.expect(pm.response.responseTime).to.be.below(200);
});

pm.test("Status code is 200 or 201", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
});

pm.test("Content-Type is JSON", function () {
    pm.response.to.be.json;
});

pm.test("Body contains success", function () {
    pm.expect(pm.response.text()).to.include("success");
});
```

**EasyAPI (Groovy):**
```groovy
pm.test("Response time is less than 200ms") {
    pm.expect(pm.response.responseTime).to.be.below(200)
}

pm.test("Status code is 200 or 201") {
    pm.expect(pm.response.code).to.be.oneOf([200, 201])
}

pm.test("Content-Type is JSON") {
    pm.response.to.be.json
}

pm.test("Body contains success") {
    pm.expect(pm.response.text()).to.include("success")
}
```

> **Difference:** Nearly identical! Just `function () {` → `{` and remove semicolons.

---

## Full API Reference

### pm

| Method/Property | Returns | Description |
|----------------|---------|-------------|
| `pm.request` | `PmRequest` | Current request object |
| `pm.response` | `PmResponse` | Current response object (post-response only) |
| `pm.environment` | `PmVariableScope` | Active environment variables |
| `pm.globals` | `PmVariableScope` | Global variables |
| `pm.collectionVariables` | `PmVariableScope` | Project-level variables |
| `pm.variables` | `PmVariableScope` | Narrowest-scope variable access |
| `pm.cookies` | `PmCookies` | Cookie access |
| `pm.test(name, closure)` | `pm` | Define a test assertion |
| `pm.test.skip(name, closure)` | `pm` | Skip a test |
| `pm.test.index()` | `int` | Number of tests run so far |
| `pm.expect(value)` | `PmExpectation` | Start a Chai-style assertion |
| `pm.sendRequest(url, closure)` | `void` | Send a GET request |
| `pm.sendRequest(options, closure)` | `void` | Send a request with options |
| `pm.info` | `PmInfo` | Script execution metadata |

### PmVariableScope

| Method | Returns | Description |
|--------|---------|-------------|
| `has(name)` | `boolean` | Check if variable exists |
| `get(name)` | `String?` | Get variable value |
| `set(name, value)` | `void` | Set variable value |
| `unset(name)` | `void` | Remove variable |
| `clear()` | `void` | Remove all variables |
| `toObject()` | `Map<String,String>` | Get all variables as a Map |
| `replaceIn(str)` | `String` | Resolve `{{$dynamic}}` variables in string |

### PmRequest

| Property | Type | Description |
|----------|------|-------------|
| `url` | `String` | Request URL |
| `method` | `String` | HTTP method |
| `headers` | `PmHeaderList` | Request headers |
| `body` | `PmRequestBody` | Request body |
| `auth` | `PmAuthConfig` | Auth configuration |

### PmHeaderList

| Method | Returns | Description |
|--------|---------|-------------|
| `add(key, value)` | `void` | Add a header |
| `add([key:..., value:...])` | `void` | Add a header (map syntax) |
| `upsert(key, value)` | `void` | Add or update a header |
| `remove(key)` | `void` | Remove a header by key |
| `get(key)` | `String?` | Get header value |
| `has(key)` | `boolean` | Check if header exists |
| `all()` | `List<Map>` | Get all headers as list of maps |

### PmRequestBody

| Property | Type | Description |
|----------|------|-------------|
| `raw` | `String?` | Raw body content (read/write) |
| `urlencoded` | `PmPropertyList` | URL-encoded form params |
| `formdata` | `PmPropertyList` | Multipart form params |
| `mode` | `String` | Body mode: "raw", "urlencoded", "formdata" |

### PmAuthConfig

| Method | Returns | Description |
|--------|---------|-------------|
| `apiKey(key, value, location)` | `void` | Set API Key auth |
| `bearer(token)` | `void` | Set Bearer Token auth |
| `basic(username, password)` | `void` | Set Basic auth |

### PmResponse

| Property | Type | Description |
|----------|------|-------------|
| `code` | `int` | HTTP status code |
| `status` | `String` | Status text |
| `headers` | `PmHeaderList` | Response headers |
| `responseTime` | `long` | Response time in ms |
| `responseSize` | `long` | Response size in bytes |

| Method | Returns | Description |
|--------|---------|-------------|
| `text()` | `String` | Response body as text |
| `json()` | `Object` | Parse body as JSON (Map/List) |
| `xml()` | `Node` | Parse body as XML |

### PmResponse BDD Properties

| Property | Description |
|----------|-------------|
| `pm.response.to.have.status(code)` | Assert status code |
| `pm.response.to.have.body(text)` | Assert body equals text |
| `pm.response.to.have.jsonBody(key)` | Assert JSON has key |
| `pm.response.to.have.jsonBody(key, value)` | Assert JSON key equals value |
| `pm.response.to.have.header(name)` | Assert header exists |
| `pm.response.to.have.jsonSchema(schema)` | Validate against JSON Schema |
| `pm.response.to.be.ok` | Assert 2xx status |
| `pm.response.to.be.json` | Assert Content-Type is JSON |
| `pm.response.to.be.html` | Assert Content-Type is HTML |
| `pm.response.to.be.xml` | Assert Content-Type is XML |
| `pm.response.to.be.error` | Assert 4xx/5xx status |
| `pm.response.to.not.have.*` | Negate any `to.have` assertion |
| `pm.response.to.not.be.*` | Negate any `to.be` assertion |

### PmExpectation

| Method | Returns | Description |
|--------|---------|-------------|
| `.to` / `.be` / `.been` / `.is` / `.that` / `.which` / `.and` / `.has` / `.have` / `.with` / `.at` / `.of` / `.same` / `.but` / `.does` | `PmExpectation` | Chainable language (no-op) |
| `.not` | `PmExpectation` | Negate the next assertion |
| `.eql(expected)` | `void` | Deep equality |
| `.equal(expected)` | `void` | Strict equality |
| `.above(n)` | `void` | Greater than |
| `.below(n)` | `void` | Less than |
| `.atLeast(n)` | `void` | Greater than or equal |
| `.atMost(n)` | `void` | Less than or equal |
| `.within(a, b)` | `void` | Between a and b inclusive |
| `.a(type)` / `.an(type)` | `void` | Type check |
| `.include(value)` / `.contain(value)` | `void` | Contains check |
| `.match(pattern)` | `void` | Regex match |
| `.lengthOf(n)` | `void` | Length check |
| `.exist` | `void` | Not null check |
| `.ok` | `void` | Truthy check |
| `.true` | `void` | Boolean true |
| `.false` | `void` | Boolean false |
| `.null` | `void` | Null check |
| `.empty` | `void` | Empty check |
| `.oneOf(list)` | `void` | Membership check |

### PmCookies

| Method | Returns | Description |
|--------|---------|-------------|
| `has(name)` | `boolean` | Check if cookie exists |
| `get(name)` | `String?` | Get cookie value |
| `toObject()` | `Map<String,String>` | All cookies as Map |

### PmInfo

| Property | Type | Description |
|----------|------|-------------|
| `eventName` | `String` | "prerequest" or "test" |
| `requestName` | `String` | Name of the current request |
| `requestId` | `String` | Unique request identifier |

---

## Legacy EasyAPI Bindings (Still Available)

The existing EasyAPI rule script bindings remain available for advanced use cases:

| Binding | Description |
|---------|-------------|
| `it` | PSI element context |
| `logger` / `LOG` | Console logging |
| `session` / `S` | Session storage |
| `tool` / `T` | RuleToolUtils |
| `regex` / `RE` | RegexUtils |
| `files` / `F` | File operations |
| `config` / `C` | Configuration access |
| `localStorage` | Local storage |
| `httpClient` | Raw HTTP client |
| `helper` / `H` | Class lookup |
| `runtime` / `R` | Project/module metadata |

> **Recommendation:** For Pre-request and Post-response scripts in the API Dashboard, prefer the `pm.*` API for Postman compatibility. Use the legacy bindings only for IDE-specific operations (PSI inspection, file I/O, etc.).
