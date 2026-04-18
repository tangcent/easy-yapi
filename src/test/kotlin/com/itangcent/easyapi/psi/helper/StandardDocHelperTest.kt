package com.itangcent.easyapi.psi.helper

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class StandardDocHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var docHelper: StandardDocHelper

    override fun setUp() {
        super.setUp()
        docHelper = StandardDocHelper.getInstance(project)
    }

    fun testHasTagReturnsTrueForExistingTag() = runBlocking {
        loadFile("helper/DocTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class DocTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.DocTagTest")!!
        assertTrue("Should find @author tag", docHelper.hasTag(psiClass, "author"))
    }

    fun testHasTagReturnsFalseForMissingTag() = runBlocking {
        loadFile("helper/DocNoTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class DocNoTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.DocNoTagTest")!!
        assertFalse("Should not find @param tag", docHelper.hasTag(psiClass, "param"))
    }

    fun testFindDocByTagReturnsValue() = runBlocking {
        loadFile("helper/DocFindTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author John Doe
             */
            public class DocFindTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.DocFindTagTest")!!
        val author = docHelper.findDocByTag(psiClass, "author")
        assertNotNull("Should find @author value", author)
        assertTrue("Author should contain 'John'", author!!.contains("John"))
    }

    fun testGetAttrOfDocCommentReturnsDescription() = runBlocking {
        loadFile("helper/DocAttrTest.java", """
            package com.test.helper;
            /**
             * This is a test class description.
             */
            public class DocAttrTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.DocAttrTest")!!
        val desc = docHelper.getAttrOfDocComment(psiClass)
        assertNotNull("Should find doc description", desc)
        assertTrue("Description should contain 'test class'", desc!!.contains("test class"))
    }

    fun testGetTagMapOfDocCommentReturnsTags() = runBlocking {
        loadFile("helper/DocTagMapTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             * @version 1.0
             */
            public class DocTagMapTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.DocTagMapTest")!!
        val tagMap = docHelper.getTagMapOfDocComment(psiClass)
        assertNotNull("Should return tag map", tagMap)
        assertTrue("Tag map should contain 'author'", tagMap.containsKey("author"))
        assertTrue("Tag map should contain 'version'", tagMap.containsKey("version"))
    }

    fun testGetAttrOfDocCommentReturnsNullForNoDoc() = runBlocking {
        loadFile("helper/NoDocTest.java", """
            package com.test.helper;
            public class NoDocTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.NoDocTest")!!
        val desc = docHelper.getAttrOfDocComment(psiClass)
        assertNull("Should return null for class without doc comment", desc)
    }
}
