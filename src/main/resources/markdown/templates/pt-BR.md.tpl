# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> INFORMAÇÕES BÁSICAS

**Caminho:** {{{api.path}}}

**Método:** {{{api.method}}}

{{#if api.description}}**Descrição:**

{{{api.description}}}

{{/if}}

> SOLICITAÇÃO

{{#if api.http.pathParams}}**Parâmetros de Caminho:**

| nome | valor | obrigatório | descrição |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Consulta:**

| nome | valor | obrigatório | descrição |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Cabeçalhos:**

| nome | valor | obrigatório | descrição |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Formulário:**

| nome | valor | obrigatório | tipo | descrição |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Corpo da Solicitação:**

| nome | tipo | descrição |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Exemplo de Solicitação:**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> RESPOSTA

**Corpo da Resposta:**

| nome | tipo | descrição |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Exemplo de Resposta:**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> INFORMAÇÕES BÁSICAS

**Protocolo:** gRPC

**Serviço:** {{{api.grpc.serviceName}}}

**Método:** {{{api.grpc.methodName}}}

**Streaming:** {{{api.grpc.streamingType}}}

**Caminho Completo:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Descrição:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> SOLICITAÇÃO

**Corpo da Solicitação:**

| nome | tipo | descrição |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Exemplo de Solicitação:**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> RESPOSTA

**Corpo da Resposta:**

| nome | tipo | descrição |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Exemplo de Resposta:**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
