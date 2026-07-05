# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> 基本資訊

**路徑：** {{{api.path}}}

**請求方式：** {{{api.method}}}

{{#if api.description}}**描述：**

{{{api.description}}}

{{/if}}

> 請求資訊

{{#if api.http.pathParams}}**路徑參數：**

| 參數名 | 範例值 | 是否必填 | 描述 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Query 參數：**

| 參數名 | 範例值 | 是否必填 | 描述 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**請求標頭：**

| 參數名 | 範例值 | 是否必填 | 描述 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**表單參數：**

| 參數名 | 範例值 | 是否必填 | 類型 | 描述 |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**請求體：**

| 參數名 | 類型 | 描述 |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**請求範例：**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> 回應資訊

**回應體：**

| 參數名 | 類型 | 描述 |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**回應範例：**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> 基本資訊

**協議：** gRPC

**服務：** {{{api.grpc.serviceName}}}

**方法：** {{{api.grpc.methodName}}}

**串流類型：** {{{api.grpc.streamingType}}}

**完整路徑：** {{{api.grpc.fullPath}}}

{{#if api.description}}**描述：**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> 請求資訊

**請求體：**

| 參數名 | 類型 | 描述 |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**請求範例：**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> 回應資訊

**回應體：**

| 參數名 | 類型 | 描述 |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**回應範例：**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
