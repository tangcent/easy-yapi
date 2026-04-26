package com.itangcent.easyapi.dashboard.script

import org.junit.Assert.*
import org.junit.Test

class PmResponseTest {

    private fun jsonResponse() = PmResponse(
        code = 200,
        status = "OK",
        headers = PmHeaderList(listOf("Content-Type" to "application/json")),
        responseTime = 150,
        responseSize = 42,
        rawBody = """{"name":"Alice","age":30}"""
    )

    private fun htmlResponse() = PmResponse(
        code = 200,
        status = "OK",
        headers = PmHeaderList(listOf("Content-Type" to "text/html")),
        responseTime = 100,
        responseSize = 200,
        rawBody = "<html><body>Hello</body></html>"
    )

    private fun errorResponse() = PmResponse(
        code = 500,
        status = "Internal Server Error",
        headers = PmHeaderList(),
        responseTime = 50,
        responseSize = 0,
        rawBody = ""
    )

    @Test
    fun testProperties() {
        val response = jsonResponse()
        assertEquals(200, response.code)
        assertEquals("OK", response.status)
        assertEquals(150, response.responseTime)
        assertEquals(42, response.responseSize)
    }

    @Test
    fun testText() {
        val response = jsonResponse()
        assertEquals("""{"name":"Alice","age":30}""", response.text())
    }

    @Test
    fun testJson() {
        val response = jsonResponse()
        val json = response.json() as? Map<*, *>
        assertNotNull(json)
        assertEquals("Alice", json!!["name"])
        assertEquals(30, json["age"])
    }

    @Test
    fun testJsonInvalidBody() {
        val response = PmResponse(
            code = 200,
            status = "OK",
            headers = PmHeaderList(),
            responseTime = 0,
            responseSize = 0,
            rawBody = "not json"
        )
        assertNull(response.json())
    }

    @Test
    fun testBddOk() {
        val response = jsonResponse()
        response.to.be.ok
        response.be.ok
    }

    @Test(expected = IllegalStateException::class)
    fun testBddOkFailsForError() {
        val response = errorResponse()
        response.to.be.ok
    }

    @Test
    fun testBddJson() {
        val response = jsonResponse()
        response.to.be.json
    }

    @Test(expected = IllegalStateException::class)
    fun testBddJsonFailsForHtml() {
        val response = htmlResponse()
        response.to.be.json
    }

    @Test
    fun testBddHtml() {
        val response = htmlResponse()
        response.to.be.html
    }

    @Test
    fun testBddError() {
        val response = errorResponse()
        response.to.be.error
    }

    @Test(expected = IllegalStateException::class)
    fun testBddErrorFailsForSuccess() {
        val response = jsonResponse()
        response.to.be.error
    }

    @Test
    fun testBddNotOk() {
        val response = errorResponse()
        response.to.not().be.ok
    }

    @Test(expected = IllegalStateException::class)
    fun testBddNotOkFailsForSuccess() {
        val response = jsonResponse()
        response.to.not().be.ok
    }

    @Test
    fun testBddNotJson() {
        val response = htmlResponse()
        response.to.not().be.json
    }

    @Test
    fun testBddNotError() {
        val response = jsonResponse()
        response.to.not().be.error
    }

    @Test
    fun testHaveStatus() {
        val response = jsonResponse()
        response.to.have.status(200)
    }

    @Test(expected = IllegalStateException::class)
    fun testHaveStatusFails() {
        val response = jsonResponse()
        response.to.have.status(404)
    }

    @Test
    fun testHaveHeader() {
        val response = jsonResponse()
        response.to.have.header("Content-Type")
    }

    @Test(expected = IllegalStateException::class)
    fun testHaveHeaderFails() {
        val response = jsonResponse()
        response.to.have.header("X-Missing")
    }

    @Test
    fun testHaveBody() {
        val response = jsonResponse()
        response.to.have.body("""{"name":"Alice","age":30}""")
    }

    @Test(expected = IllegalStateException::class)
    fun testHaveBodyFails() {
        val response = jsonResponse()
        response.to.have.body("wrong")
    }

    @Test
    fun testHaveJsonBody() {
        val response = jsonResponse()
        response.to.have.jsonBody("name")
    }

    @Test(expected = IllegalStateException::class)
    fun testHaveJsonBodyFails() {
        val response = jsonResponse()
        response.to.have.jsonBody("nonexistent")
    }

    @Test
    fun testHaveJsonBodyWithValue() {
        val response = jsonResponse()
        response.to.have.jsonBody("name", "Alice")
    }

    @Test(expected = IllegalStateException::class)
    fun testHaveJsonBodyWithValueFails() {
        val response = jsonResponse()
        response.to.have.jsonBody("name", "Bob")
    }

    @Test
    fun testNotHaveStatus() {
        val response = jsonResponse()
        response.to.not().have.status(500)
    }

    @Test(expected = IllegalStateException::class)
    fun testNotHaveStatusFails() {
        val response = jsonResponse()
        response.to.not().have.status(200)
    }

    @Test
    fun testNotHaveHeader() {
        val response = jsonResponse()
        response.to.not().have.header("X-Missing")
    }

    @Test(expected = IllegalStateException::class)
    fun testNotHaveHeaderFails() {
        val response = jsonResponse()
        response.to.not().have.header("Content-Type")
    }

    @Test
    fun testNotHaveBody() {
        val response = jsonResponse()
        response.to.not().have.body("wrong")
    }
}

class JsonSchemaValidatorTest {

    @Test
    fun testValidateStringType() {
        JsonSchemaValidator.validate("hello", mapOf("type" to "string"))
    }

    @Test(expected = IllegalStateException::class)
    fun testValidateStringTypeFails() {
        JsonSchemaValidator.validate(42, mapOf("type" to "string"))
    }

    @Test
    fun testValidateIntegerType() {
        JsonSchemaValidator.validate(42, mapOf("type" to "integer"))
    }

    @Test
    fun testValidateLongIntegerType() {
        JsonSchemaValidator.validate(42L, mapOf("type" to "integer"))
    }

    @Test
    fun testValidateNumberType() {
        JsonSchemaValidator.validate(3.14, mapOf("type" to "number"))
    }

    @Test
    fun testValidateBooleanType() {
        JsonSchemaValidator.validate(true, mapOf("type" to "boolean"))
    }

    @Test
    fun testValidateObjectType() {
        JsonSchemaValidator.validate(mapOf("key" to "value"), mapOf("type" to "object"))
    }

    @Test
    fun testValidateArrayType() {
        JsonSchemaValidator.validate(listOf(1, 2, 3), mapOf("type" to "array"))
    }

    @Test
    fun testValidateNullType() {
        JsonSchemaValidator.validate(null, mapOf("type" to "null"))
    }

    @Test
    fun testValidateRequiredProperties() {
        val schema = mapOf(
            "type" to "object",
            "required" to listOf("name", "age")
        )
        JsonSchemaValidator.validate(mapOf("name" to "Alice", "age" to 30), schema)
    }

    @Test(expected = IllegalStateException::class)
    fun testValidateRequiredPropertiesMissing() {
        val schema = mapOf(
            "type" to "object",
            "required" to listOf("name", "age")
        )
        JsonSchemaValidator.validate(mapOf("name" to "Alice"), schema)
    }

    @Test
    fun testValidateNestedProperties() {
        val schema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf("type" to "string"),
                "age" to mapOf("type" to "integer")
            )
        )
        JsonSchemaValidator.validate(mapOf("name" to "Alice", "age" to 30), schema)
    }

    @Test(expected = IllegalStateException::class)
    fun testValidateNestedPropertiesFails() {
        val schema = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "name" to mapOf("type" to "string"),
                "age" to mapOf("type" to "integer")
            )
        )
        JsonSchemaValidator.validate(mapOf("name" to "Alice", "age" to "not-a-number"), schema)
    }

    @Test
    fun testValidateArrayItems() {
        val schema = mapOf(
            "type" to "array",
            "items" to mapOf("type" to "integer")
        )
        JsonSchemaValidator.validate(listOf(1, 2, 3), schema)
    }

    @Test(expected = IllegalStateException::class)
    fun testValidateArrayItemsFails() {
        val schema = mapOf(
            "type" to "array",
            "items" to mapOf("type" to "integer")
        )
        JsonSchemaValidator.validate(listOf(1, "two", 3), schema)
    }

    @Test
    fun testValidateComplexSchema() {
        val schema = mapOf(
            "type" to "object",
            "required" to listOf("name"),
            "properties" to mapOf(
                "name" to mapOf("type" to "string"),
                "scores" to mapOf(
                    "type" to "array",
                    "items" to mapOf("type" to "number")
                )
            )
        )
        JsonSchemaValidator.validate(
            mapOf("name" to "Alice", "scores" to listOf(95.5, 88.0, 92.3)),
            schema
        )
    }
}
