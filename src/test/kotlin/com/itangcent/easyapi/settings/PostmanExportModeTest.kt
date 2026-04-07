package com.itangcent.easyapi.settings

import org.junit.Assert.*
import org.junit.Test

class PostmanExportModeTest {

    @Test
    fun testValues() {
        val values = PostmanExportMode.values()
        assertEquals(2, values.size)
        assertTrue(values.contains(PostmanExportMode.CREATE_NEW))
        assertTrue(values.contains(PostmanExportMode.UPDATE_EXISTING))
    }

    @Test
    fun testDesc() {
        assertEquals("always create new collection", PostmanExportMode.CREATE_NEW.desc)
        assertEquals("try update existed collection", PostmanExportMode.UPDATE_EXISTING.desc)
    }

    @Test
    fun testName() {
        assertEquals("CREATE_NEW", PostmanExportMode.CREATE_NEW.name)
        assertEquals("UPDATE_EXISTING", PostmanExportMode.UPDATE_EXISTING.name)
    }

    @Test
    fun testValueOf() {
        assertEquals(PostmanExportMode.CREATE_NEW, PostmanExportMode.valueOf("CREATE_NEW"))
        assertEquals(PostmanExportMode.UPDATE_EXISTING, PostmanExportMode.valueOf("UPDATE_EXISTING"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testValueOf_invalid() {
        PostmanExportMode.valueOf("INVALID")
    }
}
