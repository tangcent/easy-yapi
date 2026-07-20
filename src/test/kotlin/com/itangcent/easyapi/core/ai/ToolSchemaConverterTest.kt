package com.itangcent.easyapi.core.ai

import com.itangcent.easyapi.core.ai.tools.ProposeRuleContentTool
import com.itangcent.easyapi.core.util.json.GsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ToolSchemaConverter] (issue 1).
 *
 * The bug: tool parameter schemas were not forwarded to the model, so it
 * guessed argument names and `propose_rule_content` failed with
 * "missing required parameter(s): content, suggestedFileName".
 */
class ToolSchemaConverterTest {

    @Test
    fun testProposeRuleContentSchemaHasRequiredProperties() {
        val json = GsonUtils.toJson(ProposeRuleContentTool().parametersSchema)
        val schema = ToolSchemaConverter.toObjectSchema(json)

        assertNotNull("schema should be built from the tool's parametersSchema", schema)
        val props = schema!!.properties()
        assertTrue("schema must expose 'content'", props.containsKey("content"))
        assertTrue("schema must expose 'suggestedFileName'", props.containsKey("suggestedFileName"))
        assertEquals(
            "both params must be required",
            setOf("content", "suggestedFileName"),
            schema.required().toSet()
        )
    }

    @Test
    fun testInvalidJsonReturnsNull() {
        assertNull(ToolSchemaConverter.toObjectSchema("not-json"))
    }

    @Test
    fun testEmptyObjectSchemaHasNoProperties() {
        val schema = ToolSchemaConverter.toObjectSchema("""{"type":"object","properties":{}}""")
        assertNotNull(schema)
        assertTrue(schema!!.properties().isEmpty())
    }
}
