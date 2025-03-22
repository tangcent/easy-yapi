package com.itangcent.idea.plugin.api.export.markdown

import com.itangcent.common.model.Header
import com.itangcent.common.model.Param
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.intellij.config.resource.Resource
import com.itangcent.test.StringResource
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test cases for [TemplateMarkdownFormatter]
 */
class TemplateMarkdownFormatterTest : PluginContextLightCodeInsightFixtureTestCase() {

    fun testBasicTemplate() {
        val template = """
            ## <>{name}
            
            **Method:** <>{method}
            **Path:** <>{path}
            
            <>{if desc != null}**Description:** <>{desc}<>{end}
            
            <>{if headers}
            ### Headers
            <>{md.table(headers).title(["name":"Name","value":"Value","desc":"Description"])}
            <>{end}
            
            <>{if query}
            ### Query Parameters
            <>{md.table(query).title(["name":"Name","value":"Value","desc":"Description"])}
            <>{end}
            
            <>{if body != null}
            ### Request Body
            <>{md.objectTable(body).title([name:"Name", type:"Type", desc:"Description"])}
            <>{end}
        """.trimIndent().replace("<>", "$")

        val resource = StringResource(
            _url = "template.md",
            str = template
        )

        val formatter = TemplateMarkdownFormatter(resource)

        val request = Request().apply {
            name = "Get User"
            method = "GET"
            path = URL.of("/api/user/{id}")
            desc = "Get user information by ID"
            headers = mutableListOf(
                Header("Authorization", "Bearer token")
            )
            querys = mutableListOf(
                Param().apply {
                    name = "id"
                    value = "123"
                    desc = "User ID"
                }
            )
            body = mapOf(
                "id" to mapOf(
                    "type" to "integer",
                    "desc" to "User ID"
                )
            )
        }

        val result = formatter.parseDocs(listOf(request))

        val expected = """
# unknown

## Get User

**Method:** GET
**Path:** /api/user/{id}

**Description:** Get user information by ID


### Headers
| Name | Value | Description |
|------|------|------|
| Authorization | Bearer token |  |






### Request Body
| Name | Type | Description |
|------|------|------|
| id | object |  |
| &ensp;&ensp;&#124;─type | string |  |
| &ensp;&ensp;&#124;─desc | string |  |
        """.trimIndent()

        assertEquals(expected, result.trim())
    }

    fun testTemplateWithConditionalSections() {
        val template = """
            <>{if method == "POST"}
            ## Create <>{name}
            <>{end}
            <>{if method != "POST"}
            ## <>{name}
            <>{end}
            
            **Method:** <>{method}
            **Path:** <>{path}
            
            <>{if headers}
            ### Headers
            <>{md.table(headers).title([name:"Name", value:"Value", required:"Required", desc:"Description"])}
            <>{end}
            
            <>{if query}
            ### Query Parameters
            <>{md.table(query).title(["name":"Name","value":"Value","desc":"Description"])}
            <>{end}
            
            <>{if body != null}
            ### Request Body
            <>{md.objectTable(body).title([name:"Name", type:"Type", desc:"Description"])}
            <>{end}
        """.trimIndent().replace("<>", "$")

        val resource = StringResource(
            _url = "template.md",
            str = template
        )

        val formatter = TemplateMarkdownFormatter(resource)

        val request = Request().apply {
            name = "User"
            method = "POST"
            path = URL.of("/api/user")
            headers = mutableListOf(
                Header("Content-Type", "application/json")
            )
            body = mapOf(
                "name" to mapOf(
                    "type" to "string",
                    "desc" to "User name"
                )
            )
        }

        val result = formatter.parseDocs(listOf(request))

        val expected = """
# unknown


## Create User



**Method:** POST
**Path:** /api/user


### Headers
| Name | Value | Required | Description |
|------|------|------|------|
| Content-Type | application/json |  |  |






### Request Body
| Name | Type | Description |
|------|------|------|
| name | object |  |
| &ensp;&ensp;&#124;─type | string |  |
| &ensp;&ensp;&#124;─desc | string |  |
        """.trimIndent()

        assertEquals(expected, result.trim())
    }

    fun testTemplateWithMultipleRequests() {
        val template = """
            ## <>{name}
            
            **Method:** <>{method}
            **Path:** <>{path}
            
            <>{if desc != null}**Description:** <>{desc}<>{end}
            
            <>{if headers}
            ### Headers
            <>{md.table(headers).title([name:"Name", value:"Value", required:"Required", desc:"Description"])}
            <>{end}
            
            <>{if query}
            ### Query Parameters
            <>{md.table(query).title(["name":"Name","value":"Value","desc":"Description"])}
            <>{end}
            
            <>{if body != null}
            ### Request Body
            <>{md.objectTable(body).title([name:"Name", type:"Type", desc:"Description"])}
            <>{end}
            
            ---
        """.trimIndent().replace("<>", "$")

        val resource = StringResource(
            _url = "template.md",
            str = template
        )
        val formatter = TemplateMarkdownFormatter(resource)

        val requests = listOf(
            Request().apply {
                name = "Get User"
                method = "GET"
                path = URL.of("/api/user/{id}")
                desc = "Get user information"
            },
            Request().apply {
                name = "Create User"
                method = "POST"
                path = URL.of("/api/user")
                desc = "Create new user"
            }
        )

        val result = formatter.parseDocs(requests)

        val expected = """
# unknown

## Get User

**Method:** GET
**Path:** /api/user/{id}

**Description:** Get user information







---

## Create User

**Method:** POST
**Path:** /api/user

**Description:** Create new user







---
        """.trimIndent()

        assertEquals(expected, result.trim())
    }

    fun testUnreachableTemplate() {
        val resource = object : Resource() {
            override val url: java.net.URL
                get() = java.net.URL("file:template.md")

            override val content: String?
                get() = "template content"

            override val reachable: Boolean
                get() = false
        }

        val exception = org.junit.jupiter.api.assertThrows<IllegalStateException> {
            TemplateMarkdownFormatter(resource).parseDocs(listOf(Request()))
        }

        assertEquals("Template file not found: $resource", exception.message)
    }
} 