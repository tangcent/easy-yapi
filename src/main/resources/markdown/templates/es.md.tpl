# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> INFORMACIÓN BÁSICA

**Ruta:** {{{api.path}}}

**Método:** {{{api.method}}}

{{#if api.description}}**Descripción:**

{{{api.description}}}

{{/if}}

> SOLICITUD

{{#if api.http.pathParams}}**Parámetros de Ruta:**

| nombre | valor | requerido | descripción |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Consulta:**

| nombre | valor | requerido | descripción |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Encabezados:**

| nombre | valor | requerido | descripción |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Formulario:**

| nombre | valor | requerido | tipo | descripción |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Cuerpo de Solicitud:**

| nombre | tipo | descripción |
| ------------ | ------------ | ------------ |
{{#each api.http.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.body.demo}}

**Ejemplo de Solicitud:**

```json
{{{api.http.body.demo}}}
```
{{/if}}{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> RESPUESTA

**Cuerpo de Respuesta:**

| nombre | tipo | descripción |
| ------------ | ------------ | ------------ |
{{#each api.http.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.http.response.demo}}

**Ejemplo de Respuesta:**

```json
{{{api.http.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> INFORMACIÓN BÁSICA

**Protocolo:** gRPC

**Servicio:** {{{api.grpc.serviceName}}}

**Método:** {{{api.grpc.methodName}}}

**Transmisión:** {{{api.grpc.streamingType}}}

**Ruta Completa:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Descripción:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> SOLICITUD

**Cuerpo de Solicitud:**

| nombre | tipo | descripción |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.body.demo}}

**Ejemplo de Solicitud:**

```json
{{{api.grpc.body.demo}}}
```
{{/if}}{{/if}}{{#if api.grpc.response}}

> RESPUESTA

**Cuerpo de Respuesta:**

| nombre | tipo | descripción |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.rows as r}}| {{r.name}} | {{r.type}} | {{r.desc}} |
{{/each}}

{{#if api.grpc.response.demo}}

**Ejemplo de Respuesta:**

```json
{{{api.grpc.response.demo}}}
```
{{/if}}{{/if}}{{/if}}{{/each}}{{/each}}
