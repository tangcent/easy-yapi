${@text.boolean.true=YES}
${@text.boolean.false=NO}
${@text.null.null=-}

## $name

> BASIC

**Path:** $path

**Method:** $method

**Desc:**

$desc

> REQUEST

${if headers}
**Headers:**

${md.table(headers).title([name:"Name", value:"Value", required:"Required", desc:"Description"])}
${end}

${if paths}
**Path Parameters:**

${md.table(paths).title([name:"Name", value:"Value", required:"Required", desc:"Description"])}
${end}

${if querys}
**Query Parameters:**

${md.table(querys).title([name:"Name", value:"Value", required:"Required", desc:"Description"])}
${end}

${if form}
**Request Form:**

${md.table(form).title([name:"Name", value:"Value", required:"Required", type:"Type", desc:"Description"])}
${end}

${if body}
**Request Body:**

${md.objectTable(body).title([name:"Name", type:"Type", desc:"Description"])}

**Request Demo:**

```json
${md.json(body)}
```
${end}

> RESPONSE

${if response.headers}
**Headers:**

${md.table(response.headers).title([name:"Name", value:"Value", required:"Required", desc:"Description"])}
${end}

${if response.body}
**Body:**

${md.objectTable(response.body).title([name:"Name", type:"Type", desc:"Description"])}

**Response Demo:**

```json
${md.json5(response.body)}
```
${end}
