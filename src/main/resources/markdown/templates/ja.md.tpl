# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> 基本情報

**パス：** {{{api.path}}}

**メソッド：** {{{api.method}}}

{{#if api.description}}**説明：**

{{{api.description}}}

{{/if}}

> リクエスト

{{#if api.http.pathParams}}**パスパラメータ：**

| パラメータ名 | サンプル値 | 必須 | 説明 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**クエリパラメータ：**

| パラメータ名 | サンプル値 | 必須 | 説明 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**ヘッダー：**

| パラメータ名 | サンプル値 | 必須 | 説明 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**フォームパラメータ：**

| パラメータ名 | サンプル値 | 必須 | タイプ | 説明 |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**リクエストボディ：**

| パラメータ名 | タイプ | 説明 |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**リクエスト例：**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> レスポンス

**レスポンスボディ：**

| パラメータ名 | タイプ | 説明 |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**レスポンス例：**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> 基本情報

**プロトコル：** gRPC

**サービス：** {{{api.grpc.serviceName}}}

**メソッド：** {{{api.grpc.methodName}}}

**ストリーミング：** {{{api.grpc.streamingType}}}

**フルパス：** {{{api.grpc.fullPath}}}

{{#if api.description}}**説明：**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> リクエスト

**リクエストボディ：**

| パラメータ名 | タイプ | 説明 |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**リクエスト例：**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> レスポンス

**レスポンスボディ：**

| パラメータ名 | タイプ | 説明 |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**レスポンス例：**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
