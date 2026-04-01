package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.settings.Settings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PostmanCollectionHelperTest {

    private lateinit var settings: Settings

    @Before
    fun setUp() {
        settings = Settings()
    }

    @Test
    fun `getCollectionId returns null for null input`() {
        settings.postmanCollections = null
        
        val collectionId = PostmanCollectionHelperTestHelper.getCollectionId(settings, "module1")
        
        assertNull(collectionId)
    }

    @Test
    fun `getCollectionId returns null for blank input`() {
        settings.postmanCollections = ""
        
        val collectionId = PostmanCollectionHelperTestHelper.getCollectionId(settings, "module1")
        
        assertNull(collectionId)
    }

    @Test
    fun `getCollectionId returns null for module not found`() {
        settings.postmanCollections = "module1=collection123"
        
        val collectionId = PostmanCollectionHelperTestHelper.getCollectionId(settings, "module2")
        
        assertNull(collectionId)
    }

    @Test
    fun `getCollectionId returns collection id for existing module`() {
        settings.postmanCollections = "module1=collection123"
        
        val collectionId = PostmanCollectionHelperTestHelper.getCollectionId(settings, "module1")
        
        assertEquals("collection123", collectionId)
    }

    @Test
    fun `getCollectionId parses multiple module=collection entries`() {
        settings.postmanCollections = """
            module1=collection123
            module2=collection456
            module3=collection789
            """.trimIndent()
        
        assertEquals("collection123", PostmanCollectionHelperTestHelper.getCollectionId(settings, "module1"))
        assertEquals("collection456", PostmanCollectionHelperTestHelper.getCollectionId(settings, "module2"))
        assertEquals("collection789", PostmanCollectionHelperTestHelper.getCollectionId(settings, "module3"))
    }

    @Test
    fun `getCollectionId ignores comment lines starting with hash`() {
        settings.postmanCollections = """
            # This is a comment
            module1=collection123
            # Another comment
            module2=collection456
            """.trimIndent()
        
        assertEquals("collection123", PostmanCollectionHelperTestHelper.getCollectionId(settings, "module1"))
        assertEquals("collection456", PostmanCollectionHelperTestHelper.getCollectionId(settings, "module2"))
    }

    @Test
    fun `getCollectionId ignores blank lines`() {
        settings.postmanCollections = """
            module1=collection123

            module2=collection456
            """.trimIndent()
        
        assertEquals("collection123", PostmanCollectionHelperTestHelper.getCollectionId(settings, "module1"))
        assertEquals("collection456", PostmanCollectionHelperTestHelper.getCollectionId(settings, "module2"))
    }

    @Test
    fun `setCollectionId adds new collection to empty settings`() {
        settings.postmanCollections = null
        
        PostmanCollectionHelperTestHelper.setCollectionId(settings, "module1", "collection123")
        
        assertTrue(settings.postmanCollections?.contains("module1=collection123") == true)
    }

    @Test
    fun `setCollectionId adds new collection to existing collections`() {
        settings.postmanCollections = "module1=collection123"
        
        PostmanCollectionHelperTestHelper.setCollectionId(settings, "module2", "collection456")
        
        val result = settings.postmanCollections
        assertTrue(result?.contains("module1=collection123") == true)
        assertTrue(result?.contains("module2=collection456") == true)
    }

    @Test
    fun `setCollectionId updates existing collection`() {
        settings.postmanCollections = "module1=old-collection"
        
        PostmanCollectionHelperTestHelper.setCollectionId(settings, "module1", "new-collection")
        
        val result = settings.postmanCollections
        assertTrue(result?.contains("module1=new-collection") == true)
        assertFalse(result?.contains("old-collection") == true)
    }

    @Test
    fun `setCollectionId does not add comment lines`() {
        settings.postmanCollections = null
        
        PostmanCollectionHelperTestHelper.setCollectionId(settings, "module1", "collection123")
        
        val result = settings.postmanCollections
        assertFalse(result?.startsWith("#") == true)
        assertFalse(result?.contains("\n#") == true)
    }

    @Test
    fun `removeCollectionId removes existing collection`() {
        settings.postmanCollections = "module1=collection123\nmodule2=collection456"
        
        PostmanCollectionHelperTestHelper.removeCollectionId(settings, "module1")
        
        val result = settings.postmanCollections
        assertFalse(result?.contains("module1=collection123") == true)
        assertTrue(result?.contains("module2=collection456") == true)
    }

    @Test
    fun `removeCollectionId handles non-existent module`() {
        settings.postmanCollections = "module1=collection123"
        
        PostmanCollectionHelperTestHelper.removeCollectionId(settings, "module2")
        
        val result = settings.postmanCollections
        assertTrue(result?.contains("module1=collection123") == true)
    }

    @Test
    fun `removeCollectionId handles null input`() {
        settings.postmanCollections = null
        
        PostmanCollectionHelperTestHelper.removeCollectionId(settings, "module1")
        
        assertEquals("", settings.postmanCollections)
    }

    @Test
    fun `readCollections returns empty map for null input`() {
        settings.postmanCollections = null
        
        val collections = PostmanCollectionHelperTestHelper.readCollections(settings)
        
        assertTrue(collections.isEmpty())
    }

    @Test
    fun `readCollections returns all module=collection pairs`() {
        settings.postmanCollections = """
            module1=collection123
            module2=collection456
            """.trimIndent()
        
        val collections = PostmanCollectionHelperTestHelper.readCollections(settings)
        
        assertEquals(2, collections.size)
        assertEquals("collection123", collections["module1"])
        assertEquals("collection456", collections["module2"])
    }

    @Test
    fun `readCollections ignores comments and blank lines`() {
        settings.postmanCollections = """
            # Comment
            module1=collection123

            module2=collection456
            """.trimIndent()
        
        val collections = PostmanCollectionHelperTestHelper.readCollections(settings)
        
        assertEquals(2, collections.size)
    }
}

object PostmanCollectionHelperTestHelper {
    fun getCollectionId(settings: Settings, module: String): String? {
        val properties = java.util.Properties()
        settings.postmanCollections?.byteInputStream()?.let { properties.load(it) }
        return properties.getProperty(module)
    }

    fun setCollectionId(settings: Settings, module: String, collectionId: String) {
        val properties = java.util.Properties()
        settings.postmanCollections?.byteInputStream()?.let { properties.load(it) }
        properties[module] = collectionId
        val output = java.io.ByteArrayOutputStream()
        properties.store(output, "")
        settings.postmanCollections = output.toString().removePropertiesComments()
    }

    fun removeCollectionId(settings: Settings, module: String) {
        val properties = java.util.Properties()
        settings.postmanCollections?.byteInputStream()?.let { properties.load(it) }
        properties.remove(module)
        val output = java.io.ByteArrayOutputStream()
        properties.store(output, "")
        settings.postmanCollections = output.toString().removePropertiesComments()
    }

    fun readCollections(settings: Settings): Map<String, String> {
        val properties = java.util.Properties()
        settings.postmanCollections?.byteInputStream()?.let { properties.load(it) }
        return properties.entries.associate { (it.key as String) to (it.value as String) }
    }

    private fun String.removePropertiesComments(): String {
        var ret = this
        while (ret.startsWith("#") && ret.contains('\n')) {
            if (ret.substringBefore('\n').contains('=')) {
                break
            }
            ret = ret.substringAfter('\n')
        }
        return ret
    }
}
