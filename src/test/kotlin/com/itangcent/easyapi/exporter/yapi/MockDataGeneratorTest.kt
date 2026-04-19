package com.itangcent.easyapi.exporter.yapi

import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.ParameterType
import org.junit.Assert.*
import org.junit.Test

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
        val param = ApiParameter(name = "email", jsonType = "string")
        assertEquals("@email", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForPhoneField() {
        val param = ApiParameter(name = "phone", jsonType = "string")
        assertEquals("@phone", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForIntegerField() {
        val param = ApiParameter(name = "count", jsonType = "integer")
        assertEquals("@integer(0, 100)", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForUnknownFieldWithStringType() {
        val param = ApiParameter(name = "data", jsonType = "string")
        assertEquals("@string", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForUnknownFieldWithIntegerType() {
        val param = ApiParameter(name = "data", jsonType = "integer")
        assertEquals("@integer", emptyGenerator.mockFor(param))
    }

    @Test
    fun testMockForUnknownFieldWithNoJsonType() {
        val param = ApiParameter(name = "data")
        assertEquals("@string", emptyGenerator.mockFor(param))
    }

    // endregion

    // region mockFor with custom rules

    @Test
    fun testMockForWithRuleNameAndType() {
        val param = ApiParameter(name = "email", jsonType = "string")
        assertEquals("@email", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForWithRuleWildcardNameAndType() {
        val param = ApiParameter(name = "phone", jsonType = "string")
        assertEquals("@phone", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForWithRuleWildcardNameOnly() {
        val param = ApiParameter(name = "age", jsonType = "integer")
        assertEquals("@integer(0, 120)", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForWithRuleExactName() {
        val param = ApiParameter(name = "customField", jsonType = "string")
        assertEquals("@custom", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForWithRuleWildcardTypeFallback() {
        val param = ApiParameter(name = "data", jsonType = "integer")
        val result = rulesGenerator.mockFor(param)
        assertEquals("@integer", result)
    }

    @Test
    fun testMockForRuleFallbackToNameHeuristic() {
        val param = ApiParameter(name = "address", jsonType = "string")
        assertEquals("@county(true)", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForRuleFallbackToTypeHeuristic() {
        val param = ApiParameter(name = "unknownField", jsonType = "boolean")
        assertEquals("@boolean", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForIdWithIntegerType() {
        val param = ApiParameter(name = "id", jsonType = "integer")
        assertEquals("@integer", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForIdWithStringType() {
        val param = ApiParameter(name = "id", jsonType = "string")
        assertEquals("@id", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForPriceWithDoubleType() {
        val param = ApiParameter(name = "price", jsonType = "double")
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
    fun testMockForUsesJsonTypeOverParameterType() {
        val param = ApiParameter(
            name = "email",
            type = ParameterType.TEXT,
            jsonType = "string"
        )
        assertEquals("@email", rulesGenerator.mockFor(param))
    }

    @Test
    fun testMockForFallsBackToParameterTypeRawType() {
        val param = ApiParameter(
            name = "email",
            type = ParameterType.TEXT
        )
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
        val param = ApiParameter(name = "email", jsonType = "string")
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
        val param = ApiParameter(name = "email", jsonType = "string")
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
        val param = ApiParameter(name = "email", jsonType = "string")
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
        val param = ApiParameter(name = "email", jsonType = "string")
        assertEquals("@wildcard_type", generator.mockFor(param))
    }

    // endregion
}
