package com.itangcent.idea.sqlite

import com.google.inject.Inject
import com.itangcent.common.utils.FileUtils
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import java.io.File
import kotlin.test.assertEquals

/**
 * Test case of [SqliteDataResourceHelper]
 */
@DisabledOnOs(OS.WINDOWS)
internal class SqliteDataResourceHelperTest : AdvancedContextTest() {

    @Inject
    lateinit var sqliteDataResourceHelper: SqliteDataResourceHelper

    @Test
    fun testSimpleBeanDAO() {
        val aDAO = sqliteDataResourceHelper.getSimpleBeanDAO("$tempDir${File.separator}simple.db", "cacheA")
        assertEquals(null, aDAO.get("abc"))
        aDAO.set("abc", "123")
        assertEquals("123", aDAO.get("abc"))
        aDAO.set("abc", "456")
        assertEquals("456", aDAO.get("abc"))
        aDAO.delete("abc")
        assertEquals(null, aDAO.get("abc"))
    }

    @Test
    fun testExpiredBeanDAO() {
        val aDAO = sqliteDataResourceHelper.getExpiredBeanDAO("$tempDir${File.separator}simple.db", "cacheAExpired")
        assertEquals(null, aDAO.get("abc"))
        aDAO.set("abc", "123", System.currentTimeMillis() + 2000)
        assertEquals("123", aDAO.get("abc"))
        aDAO.delete("abc")
        assertEquals(null, aDAO.get("abc"))
        aDAO.set("abc", "456", System.currentTimeMillis() + 1000)
        assertEquals("456", aDAO.get("abc"))
        Thread.sleep(1001)
        assertEquals(null, aDAO.get("abc"))
    }
}