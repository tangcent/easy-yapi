package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.MutableExtension
import com.itangcent.easyapi.core.export.ParameterType
import com.itangcent.easyapi.core.psi.type.IrType
import org.junit.Assert.*
import org.junit.Test

private fun apiParam(
    name: String,
    jsonType: String? = null,
    type: ParameterType = ParameterType.TEXT
): ApiParameter = ApiParameter(
    name = name,
    type = type,
    extensions = MutableExtension().apply { if (jsonType != null) this["jsonType"] = jsonType }
)

class MockDataGeneratorTest {

    private val emptyGenerator = MockDataGenerator()

    private val rulesGenerator = MockDataGenerator(
        mapOf(
            "*.email|string" to "@email",
            "*.phone|string" to "@phone",
            "*.id|integer" to "@integer",
            "*.id|long" to "@integer",
            "*.id|string" to "@id",
            "*.age|integer" to "@integer(0, 120)",
            "*.price|double" to "@float(0, 10000, 2, 2)",
            "*.count|integer" to "@integer(0, 100)",
            "*.page|integer" to "@integer(1, 100)",
            "*.password|string" to "******",
            "*.token|string" to "@string(32)",
            "customField" to "@custom",
            "param.userId" to "@id",
            "query.searchTerm" to "@word"
        )
    )

    // region mockFor with empty rules (name/type heuristics)

    @Test
    fun testMockForEmailField() {
        val param = apiParam(name = "email", jsonType = "string")
        assertEquals("@email", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForPhoneField() {
        val param = apiParam(name = "phone", jsonType = "string")
        assertEquals("@phone", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForIntegerField() {
        val param = apiParam(name = "count", jsonType = "integer")
        assertEquals("@integer(0, 100)", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForUnknownFieldWithStringType() {
        val param = apiParam(name = "data", jsonType = "string")
        assertEquals("@string", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForUnknownFieldWithIntegerType() {
        val param = apiParam(name = "data", jsonType = "integer")
        assertEquals("@integer", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForUnknownFieldWithNoIrType() {
        val param = ApiParameter(name = "data")
        assertEquals("@string", emptyGenerator.mockFor(param))
    }

    // endregion

    // region mockFor with custom rules

    @Test
    fun testMockForWithRuleNameAndType() {
        val param = apiParam(name = "email", jsonType = "string")
        assertEquals("@email", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForWithRuleWildcardNameAndType() {
        val param = apiParam(name = "phone", jsonType = "string")
        assertEquals("@phone", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForWithRuleWildcardNameOnly() {
        val param = apiParam(name = "age", jsonType = "integer")
        assertEquals("@integer(0, 120)", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForWithRuleExactName() {
        val param = apiParam(name = "customField", jsonType = "string")
        assertEquals("@custom", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForWithRuleWildcardTypeFallback() {
        val param = apiParam(name = "data", jsonType = "integer")
        val result = rulesGenerator.mockFor(param)
        assertEquals("@integer", result)
    }

    @Test
    fun testMockForRuleFallbackToNameHeuristic() {
        val param = apiParam(name = "address", jsonType = "string")
        assertEquals("@county(true)", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForRuleFallbackToTypeHeuristic() {
        val param = apiParam(name = "unknownField", jsonType = "boolean")
        assertEquals("@boolean", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForIdWithIntegerType() {
        val param = apiParam(name = "id", jsonType = "integer")
        assertEquals("@integer", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForIdWithStringType() {
        val param = apiParam(name = "id", jsonType = "string")
        assertEquals("@id", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForPriceWithDoubleType() {
        val param = apiParam(name = "price", jsonType = "double")
        assertEquals("@float(0, 10000, 2, 2)", rulesGenerator.mockFor(param))
    }

    // endregion

    // region mockForParam

    @Test
    fun testMockForParamWithNameHeuristic() {
        assertEquals("@id", emptyGenerator.mockForParam("userId"))
    }

    @Test
    fun testMockForParamWithRule() {
        assertEquals("@id", rulesGenerator.mockForParam("userId"))
    }

    @Test
    fun testMockForParamWithNoMatch() {
        assertNull(emptyGenerator.mockForParam("xyz"))
    }

    // endregion

    // region mockForQuery

    @Test
    fun testMockForQueryWithNameHeuristic() {
        assertEquals("@id", emptyGenerator.mockForQuery("userId"))
    }

    @Test
    fun testMockForQueryWithRule() {
        assertEquals("@word", rulesGenerator.mockForQuery("searchTerm"))
    }

    @Test
    fun testMockForQueryWithNoMatch() {
        assertNull(emptyGenerator.mockForQuery("xyz"))
    }

    // endregion

    // region jsonType vs ParameterType

    @Test
    fun testMockForUsesIrTypeOverParameterType() {
        val param = apiParam(name = "email", jsonType = "string")
        assertEquals("@email", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForFallsBackToParameterTypeRawType() {
        val param = apiParam(name = "email")
        val result = emptyGenerator.mockFor(param)
        assertNotNull(result)
    }

    // endregion

    // region pattern priority

    @Test
    fun testPatternPriorityNameAndTypeOverWildcardNameAndType() {
        val generator = MockDataGenerator(
            mapOf(
                "email|string" to "@exact",
                "*.email|string" to "@wildcard"
            )
        )
        val param = apiParam(name = "email", jsonType = "string")
        assertEquals("@exact", generator.mockFor(param))
    }

    @Test
    fun testPatternPriorityWildcardNameAndTypeOverWildcardName() {
        val generator = MockDataGenerator(
            mapOf(
                "*.email|string" to "@wildcard_typed",
                "*.email" to "@wildcard"
            )
        )
        val param = apiParam(name = "email", jsonType = "string")
        assertEquals("@wildcard_typed", generator.mockFor(param))
    }

    @Test
    fun testPatternPriorityWildcardNameOverWildcardType() {
        val generator = MockDataGenerator(
            mapOf(
                "*.email" to "@wildcard_name",
                "*|string" to "@wildcard_type"
            )
        )
        val param = apiParam(name = "email", jsonType = "string")
        assertEquals("@wildcard_name", generator.mockFor(param))
    }

    @Test
    fun testPatternPriorityWildcardTypeOverExactName() {
        val generator = MockDataGenerator(
            mapOf(
                "*|string" to "@wildcard_type",
                "email" to "@exact_name"
            )
        )
        val param = apiParam(name = "email", jsonType = "string")
        assertEquals("@wildcard_type", generator.mockFor(param))
    }

    // endregion

    // region IrType coverage — this channel must translate every core word

    /**
     * Mock generation is one of the exits that must translate an IR word into something native
     * to the target protocol (here: YApi's `@…` mock syntax). Pinning the table to
     * [IrType.ALL_TYPES] means a new IR word fails here until this channel has decided what to
     * generate for it — rather than silently exporting a parameter with no mock at all.
     */
    @Test
    fun testEveryCoreIrTypeHasAMockExpression() {
        val unmapped = IrType.ALL_TYPES.filter {
            emptyGenerator.mockFor(apiParam(name = "field", jsonType = it)) == null
        }
        assertEquals(
            "every core IR word needs a YApi mock expression",
            emptyList<String>(),
            unmapped
        )
    }

    /**
     * A form parameter with no IR word falls back to the wire-level word from
     * [ParameterType.rawType] (`text` / `file`). `file` is already an IR word, but `text` is not,
     * so it needs its own branch — this pins that.
     */
    @Test
    fun testWireLevelParameterTypeAlsoProducesAMock() {
        assertEquals("@string", emptyGenerator.mockFor(apiParam(name = "field")))
        assertEquals(
            "@file",
            emptyGenerator.mockFor(apiParam(name = "field", type = ParameterType.FILE))
        )
    }

    // endregion
}
