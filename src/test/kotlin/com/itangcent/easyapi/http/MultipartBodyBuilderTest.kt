package com.itangcent.easyapi.http

import org.junit.Assert.*
import org.junit.Test

class MultipartBodyBuilderTest {

    @Test
    fun testBuild_textParam() {
        val params = listOf(FormParam.Text("username", "john"))
        val result = MultipartBodyBuilder.build(params)

        assertNotNull(result)
        assertTrue(result.contentType.startsWith("multipart/form-data; boundary="))
        val body = String(result.bytes)
        assertTrue(body.contains("Content-Disposition: form-data; name=\"username\""))
        assertTrue(body.contains("john"))
    }

    @Test
    fun testBuild_fileParam() {
        val fileBytes = "file content".toByteArray()
        val params = listOf(FormParam.File("avatar", "photo.jpg", "image/jpeg", fileBytes))
        val result = MultipartBodyBuilder.build(params)

        assertNotNull(result)
        val body = String(result.bytes)
        assertTrue(body.contains("Content-Disposition: form-data; name=\"avatar\"; filename=\"photo.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertTrue(body.contains("file content"))
    }

    @Test
    fun testBuild_fileParam_noContentType() {
        val fileBytes = "data".toByteArray()
        val params = listOf(FormParam.File("file", "data.bin", null, fileBytes))
        val result = MultipartBodyBuilder.build(params)

        val body = String(result.bytes)
        assertTrue(body.contains("Content-Type: application/octet-stream"))
    }

    @Test
    fun testBuild_multipleParams() {
        val params = listOf(
            FormParam.Text("name", "John"),
            FormParam.Text("email", "john@example.com"),
            FormParam.File("avatar", "photo.jpg", "image/jpeg", "img".toByteArray())
        )
        val result = MultipartBodyBuilder.build(params)

        val body = String(result.bytes)
        assertTrue(body.contains("name=\"name\""))
        assertTrue(body.contains("John"))
        assertTrue(body.contains("name=\"email\""))
        assertTrue(body.contains("john@example.com"))
        assertTrue(body.contains("name=\"avatar\""))
    }

    @Test
    fun testBuild_emptyParams() {
        val result = MultipartBodyBuilder.build(emptyList())

        assertNotNull(result)
        assertTrue(result.contentType.startsWith("multipart/form-data; boundary="))
        val body = String(result.bytes)
        assertTrue(body.endsWith("--\r\n"))
    }

    @Test
    fun testBuild_boundaryIsUnique() {
        val params = listOf(FormParam.Text("key", "value"))
        val result1 = MultipartBodyBuilder.build(params)
        val result2 = MultipartBodyBuilder.build(params)

        val boundary1 = result1.contentType.substringAfter("boundary=")
        val boundary2 = result2.contentType.substringAfter("boundary=")
        assertNotEquals(boundary1, boundary2)
    }

    @Test
    fun testBuild_boundaryInBody() {
        val params = listOf(FormParam.Text("key", "value"))
        val result = MultipartBodyBuilder.build(params)

        val boundary = result.contentType.substringAfter("boundary=")
        val body = String(result.bytes)
        assertTrue(body.contains("--$boundary"))
        assertTrue(body.contains("--$boundary--"))
    }

    @Test
    fun testBuild_crlfLineEndings() {
        val params = listOf(FormParam.Text("key", "value"))
        val result = MultipartBodyBuilder.build(params)

        val body = String(result.bytes)
        assertTrue(body.contains("\r\n"))
    }

    @Test
    fun testMultipartBody_dataClass() {
        val body1 = MultipartBodyBuilder.MultipartBody("multipart/form-data; boundary=abc", byteArrayOf(1, 2, 3))
        val body2 = MultipartBodyBuilder.MultipartBody("multipart/form-data; boundary=abc", byteArrayOf(1, 2, 3))
        assertEquals(body1.contentType, body2.contentType)
        assertArrayEquals(body1.bytes, body2.bytes)
    }
}
