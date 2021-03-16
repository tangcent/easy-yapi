package com.itangcent.common.model

import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.utils.Extensible
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.*

/**
 * Test case for [KitUtils]
 */
@ExtendWith
class ModelsTest {

    @ParameterizedTest
    @ValueSource(classes = [Doc::class, FormParam::class,
        Header::class, MethodDoc::class,
        Param::class, PathParam::class,
        Request::class, Response::class
    ])
    fun testExtensible(cls: Class<Extensible>) {
        val extensible = cls.newInstance() as Extensible//{}
        assertFalse(extensible.hasExt("a"))
        assertFalse(extensible.hasAnyExt("a", "b"))
        extensible.setExt("b", "1")//{b:1}
        assertFalse(extensible.hasExt("a"))
        assertTrue(extensible.hasAnyExt("a", "b"))
        assertEquals(null, extensible.getExt("a"))
        assertEquals("1", extensible.getExt<Any>("b"))
        assertEquals(mapOf("b" to "1"), extensible.exts())
        assertEquals(extensible, extensible)
        assertNotEquals<Any?>(extensible, null)
        assertDoesNotThrow { extensible.toString() }
        assertDoesNotThrow { extensible.hashCode() }
    }

    @Test
    fun testRequest() {
        val request = Request()

        //test empty request
        assertFalse(request.hasForm())
        assertFalse(request.hasBodyOrForm())
        assertFalse(request.hasMethod())
        assertNull(request.getContentType())
        assertNull(request.header("content-type"))

        //test empty request with GET
        request.method = HttpMethod.GET

        assertFalse(request.hasForm())
        assertFalse(request.hasBodyOrForm())
        assertTrue(request.hasMethod())
        assertNull(request.getContentType())
        assertNull(request.header("content-type"))


        //test empty request with POST
        request.method = HttpMethod.POST

        assertFalse(request.hasForm())
        assertTrue(request.hasBodyOrForm())
        assertTrue(request.hasMethod())
        assertNull(request.getContentType())
        assertNull(request.header("content-type"))
    }
}