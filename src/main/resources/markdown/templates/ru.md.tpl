# {{{moduleName}}}
{{#each groups as group}}{{#if group.folder}}

## {{{group.folder}}}
{{/if}}{{#each group.endpoints as api}}{{#if api.http}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.method}}} {{{api.path}}}{{/if}}

> ОСНОВНАЯ ИНФОРМАЦИЯ

**Путь:** {{{api.path}}}

**Метод:** {{{api.method}}}

{{#if api.description}}**Описание:**

{{{api.description}}}

{{/if}}

> ЗАПРОС

{{#if api.http.pathParams}}**Параметры пути:**

| имя | значение | обязательно | описание |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.pathParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.queryParams}}**Параметры запроса:**

| имя | значение | обязательно | описание |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.queryParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.headers}}**Заголовки:**

| имя | значение | обязательно | описание |
| ------------ | ------------ | ------------ | ------------ |
{{#each api.http.headers as h}}| {{h.name}} | {{h.value}} | {{requiredLabel h.required}} | {{h.description}} |
{{/each}}

{{/if}}{{#if api.http.formParams}}**Форма:**

| имя | значение | обязательно | тип | описание |
| ------------ | ------------ | ------------ | ------------ | ------------ |
{{#each api.http.formParams as p}}| {{p.name}} | {{p.defaultValue}} | {{requiredLabel p.required}} | {{p.type}} | {{p.description}} |
{{/each}}

{{/if}}{{#if api.http.body}}**Тело запроса:**

| имя | тип | описание |
| ------------ | ------------ | ------------ |
{{#each api.http.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Пример запроса:**

```json
{{{api.http.body.asDemo()}}}
```
{{/if}}{{#if api.http.hasRequestContent}}{{else}}

{{/if}}
{{#if api.http.response}}

> ОТВЕТ

**Тело ответа:**

| имя | тип | описание |
| ------------ | ------------ | ------------ |
{{#each api.http.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Пример ответа:**

```json
{{{api.http.response.asDemo()}}}
```
{{/if}}{{/if}}{{#if api.grpc}}

---
### {{#if api.name}}{{{api.name}}}{{else}}{{{api.path}}}{{/if}}

> ОСНОВНАЯ ИНФОРМАЦИЯ

**Протокол:** gRPC

**Сервис:** {{{api.grpc.serviceName}}}

**Метод:** {{{api.grpc.methodName}}}

**Потоковая передача:** {{{api.grpc.streamingType}}}

**Полный путь:** {{{api.grpc.fullPath}}}

{{#if api.description}}**Описание:**

{{{api.description}}}

{{/if}}{{#if api.grpc.body}}

> ЗАПРОС

**Тело запроса:**

| имя | тип | описание |
| ------------ | ------------ | ------------ |
{{#each api.grpc.body.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Пример запроса:**

```json
{{{api.grpc.body.asDemo()}}}
```
{{/if}}{{#if api.grpc.response}}

> ОТВЕТ

**Тело ответа:**

| имя | тип | описание |
| ------------ | ------------ | ------------ |
{{#each api.grpc.response.fields as f}}| {{{f.indent}}}{{f.name}} | {{f.type}} | {{f.desc}} |
{{/each}}

**Пример ответа:**

```json
{{{api.grpc.response.asDemo()}}}
```
{{/if}}{{/if}}{{/each}}{{/each}}
