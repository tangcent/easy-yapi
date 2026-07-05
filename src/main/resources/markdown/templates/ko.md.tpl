# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> 기본 정보

**경로:** {{{api.path}}}

**메서드:** {{{api.method}}}

{{#if api.description}}**설명:**

{{{api.description}}}

{{/if}}

> 요청

{{#if api.http.pathParams}}**경로 매개변수:**

| 매개변수명 | 샘플값 | 필수 | 설명 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**쿼리 매개변수:**

| 매개변수명 | 샘플값 | 필수 | 설명 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**헤더:**

| 매개변수명 | 샘플값 | 필수 | 설명 |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**폼 매개변수:**

| 매개변수명 | 샘플값 | 필수 | 타입 | 설명 |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**요청 본문:**

| 매개변수명 | 타입 | 설명 |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**요청 예시:**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> 응답

**응답 본문:**

| 매개변수명 | 타입 | 설명 |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**응답 예시:**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> 기본 정보

**프로토콜:** gRPC

**서비스:** {{{api.grpc.serviceName}}}

**메서드:** {{{api.grpc.methodName}}}

**스트리밍:** {{{api.grpc.streamingType}}}

**전체 경로:** {{{api.grpc.fullPath}}}

{{#if api.description}}**설명:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> 요청

**요청 본문:**

| 매개변수명 | 타입 | 설명 |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**요청 예시:**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> 응답

**응답 본문:**

| 매개변수명 | 타입 | 설명 |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**응답 예시:**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
