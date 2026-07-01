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
{{#each api.http.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.body.demo}}

**リクエスト例：**

```json
{{{api.http.body.demo}}}
```
{{/if}}{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> レスポンス

**レスポンスボディ：**

| パラメータ名 | タイプ | 説明 |
| ------------ | ------------ | ------------ |
{{#each api.http.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.response.demo}}

**レスポンス例：**

```json
{{{api.http.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{#if api.grpc}}

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
{{#each api.grpc.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.body.demo}}

**リクエスト例：**

```json
{{{api.grpc.body.demo}}}
```
{{/if}}{{/if}}{{#if api.grpc.response}}

> レスポンス

**レスポンスボディ：**

| パラメータ名 | タイプ | 説明 |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.response.demo}}

**レスポンス例：**

```json
{{{api.grpc.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{/each}}{{/each}}
