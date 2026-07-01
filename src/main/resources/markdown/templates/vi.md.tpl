# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> CƠ BẢN

**Đường dẫn:** {{{api.path}}}

**Phương thức:** {{{api.method}}}

{{#if api.description}}**Mô tả:**

{{{api.description}}}

{{/if}}

> YÊU CẦU

{{#if api.http.pathParams}}**Tham số đường dẫn:**

| tên | giá trị | bắt buộc | mô tả |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Truy vấn:**

| tên | giá trị | bắt buộc | mô tả |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Tiêu đề:**

| tên | giá trị | bắt buộc | mô tả |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Biểu mẫu:**

| tên | giá trị | bắt buộc | loại | mô tả |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Nội dung yêu cầu:**

| tên | loại | mô tả |
| ------------ | ------------ | ------------ |
{{#each api.http.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.body.demo}}

**Mẫu yêu cầu:**

```json
{{{api.http.body.demo}}}
```
{{/if}}{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> PHẢN HỒI

**Nội dung:**

| tên | loại | mô tả |
| ------------ | ------------ | ------------ |
{{#each api.http.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.response.demo}}

**Mẫu phản hồi:**

```json
{{{api.http.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> CƠ BẢN

**Giao thức:** gRPC

**Dịch vụ:** {{{api.grpc.serviceName}}}

**Phương thức:** {{{api.grpc.methodName}}}

**Luồng:** {{{api.grpc.streamingType}}}

**Đường dẫn đầy đủ:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Mô tả:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> YÊU CẦU

**Nội dung yêu cầu:**

| tên | loại | mô tả |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.body.demo}}

**Mẫu yêu cầu:**

```json
{{{api.grpc.body.demo}}}
```
{{/if}}{{/if}}{{#if api.grpc.response}}

> PHẢN HỒI

**Nội dung:**

| tên | loại | mô tả |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.response.demo}}

**Mẫu phản hồi:**

```json
{{{api.grpc.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{/each}}{{/each}}
