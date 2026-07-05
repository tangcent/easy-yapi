# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> พื้นฐาน

**เส้นทาง:** {{{api.path}}}

**เมธอด:** {{{api.method}}}

{{#if api.description}}**คำอธิบาย:**

{{{api.description}}}

{{/if}}

> คำขอ

{{#if api.http.pathParams}}**พารามิเตอร์เส้นทาง:**

| ชื่อ | ค่า | ต้องระบุ | คำอธิบาย |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**คิวรี:**

| ชื่อ | ค่า | ต้องระบุ | คำอธิบาย |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**ส่วนหัว:**

| ชื่อ | ค่า | ต้องระบุ | คำอธิบาย |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**แบบฟอร์ม:**

| ชื่อ | ค่า | ต้องระบุ | ประเภท | คำอธิบาย |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**เนื้อหาคำขอ:**

| ชื่อ | ประเภท | คำอธิบาย |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**ตัวอย่างคำขอ:**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> การตอบกลับ

**เนื้อหา:**

| ชื่อ | ประเภท | คำอธิบาย |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**ตัวอย่างการตอบกลับ:**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> พื้นฐาน

**โปรโตคอล:** gRPC

**บริการ:** {{{api.grpc.serviceName}}}

**เมธอด:** {{{api.grpc.methodName}}}

**สตรีมมิ่ง:** {{{api.grpc.streamingType}}}

**เส้นทางเต็ม:** {{{api.grpc.fullPath}}}

{{#if api.description}}**คำอธิบาย:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> คำขอ

**เนื้อหาคำขอ:**

| ชื่อ | ประเภท | คำอธิบาย |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**ตัวอย่างคำขอ:**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> การตอบกลับ

**เนื้อหา:**

| ชื่อ | ประเภท | คำอธิบาย |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**ตัวอย่างการตอบกลับ:**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
