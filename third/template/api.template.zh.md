${@text.boolean.true=是}
${@text.boolean.false=否}
${@text.null.null=-}

## $name

> 基本信息

**路径:** $path

**方法:** $method

**描述:**

$desc

> 请求

${if headers}
**请求头:**

${md.table(headers).title([name:"名称", value:"值", required:"是否必需", desc:"描述"])}
${end}

${if paths}
**路径参数:**

${md.table(paths).title([name:"名称", value:"值", required:"是否必需", desc:"描述"])}
${end}

${if querys}
**查询参数:**

${md.table(querys).title([name:"名称", value:"值", required:"是否必需", desc:"描述"])}
${end}

${if form}
**表单数据:**

${md.table(form).title([name:"名称", value:"值", required:"是否必需", type:"类型", desc:"描述"])}
${end}

${if body}
**请求体:**

${md.objectTable(body).title([name:"名称", type:"类型", desc:"描述"])}

**请求示例:**

```json
${md.json(body)}
```
${end}

> 响应

${if response.headers}
**响应头:**

${md.table(response.headers).title([name:"名称", value:"值", required:"是否必需", desc:"描述"])}
${end}

${if response.body}
**响应体:**

${md.objectTable(response.body).title([name:"名称", type:"类型", desc:"描述"])}

**响应示例:**

```json
${md.json5(response.body)}
```
${end} 