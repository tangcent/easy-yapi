# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> DASAR

**Jalur:** {{{api.path}}}

**Metode:** {{{api.method}}}

{{#if api.description}}**Deskripsi:**

{{{api.description}}}

{{/if}}

> PERMINTAAN

{{#if api.http.pathParams}}**Parameter Jalur:**

| nama | nilai | wajib | deskripsi |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Kueri:**

| nama | nilai | wajib | deskripsi |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Header:**

| nama | nilai | wajib | deskripsi |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Formulir:**

| nama | nilai | wajib | tipe | deskripsi |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Isi Permintaan:**

| nama | tipe | deskripsi |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Contoh Permintaan:**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> TANGGAPAN

**Isi:**

| nama | tipe | deskripsi |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Contoh Tanggapan:**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> DASAR

**Protokol:** gRPC

**Layanan:** {{{api.grpc.serviceName}}}

**Metode:** {{{api.grpc.methodName}}}

**Streaming:** {{{api.grpc.streamingType}}}

**Jalur Lengkap:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Deskripsi:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> PERMINTAAN

**Isi Permintaan:**

| nama | tipe | deskripsi |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Contoh Permintaan:**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> TANGGAPAN

**Isi:**

| nama | tipe | deskripsi |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Contoh Tanggapan:**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
