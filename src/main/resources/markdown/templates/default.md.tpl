# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> BASIC

**Path:** {{{api.path}}}

**Method:** {{{api.method}}}

{{#if api.description}}**Desc:**

{{{api.description}}}

{{/if}}

> REQUEST

{{#if api.http.pathParams}}**Path Params:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Query:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Headers:**

| name | value | required | desc |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Form:**

| name | value | required | type | desc |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Request Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Request Demo:**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> RESPONSE

**Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Response Demo:**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> BASIC

**Protocol:** gRPC

**Service:** {{{api.grpc.serviceName}}}

**Method:** {{{api.grpc.methodName}}}

**Streaming:** {{{api.grpc.streamingType}}}

**Full Path:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Desc:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> REQUEST

**Request Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Request Demo:**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> RESPONSE

**Body:**

| name | type | desc |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Response Demo:**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
