package com.itangcent.easyapi.psi.helper

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class UnifiedDocHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var docHelper: UnifiedDocHelper

    override fun setUp() {
        super.setUp()
        docHelper = UnifiedDocHelper.getInstance(project)
    }

    // --- hasTag ---

    fun testHasTagReturnsTrueForExistingTag() = runBlocking {
        loadFile("helper/UnifiedDocTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class UnifiedDocTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocTagTest")!!
        assertTrue("Should find @author tag", docHelper.hasTag(psiClass, "author"))
    }

    fun testHasTagReturnsFalseForMissingTag() = runBlocking {
        loadFile("helper/UnifiedDocNoTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class UnifiedDocNoTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocNoTagTest")!!
        assertFalse("Should not find @param tag", docHelper.hasTag(psiClass, "param"))
    }

    fun testHasTagReturnsFalseForNullElement() = runBlocking {
        assertFalse("Should return false for null element", docHelper.hasTag(null, "author"))
    }

    fun testHasTagReturnsFalseForNullTag() = runBlocking {
        loadFile("helper/UnifiedDocNullTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class UnifiedDocNullTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocNullTagTest")!!
        assertFalse("Should return false for null tag", docHelper.hasTag(psiClass, null))
    }

    fun testHasTagReturnsFalseForElementWithoutDoc() = runBlocking {
        loadFile("helper/UnifiedDocNoDocTagTest.java", """
            package com.test.helper;
            public class UnifiedDocNoDocTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocNoDocTagTest")!!
        assertFalse("Should return false for element without doc", docHelper.hasTag(psiClass, "author"))
    }

    // --- findDocByTag ---

    fun testFindDocByTagReturnsValue() = runBlocking {
        loadFile("helper/UnifiedDocFindTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author John Doe
             */
            public class UnifiedDocFindTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocFindTagTest")!!
        val author = docHelper.findDocByTag(psiClass, "author")
        assertNotNull("Should find @author value", author)
        assertTrue("Author should contain 'John'", author!!.contains("John"))
    }

    fun testFindDocByTagReturnsNullForNullElement() = runBlocking {
        assertNull("Should return null for null element", docHelper.findDocByTag(null, "author"))
    }

    fun testFindDocByTagReturnsNullForNullTag() = runBlocking {
        loadFile("helper/UnifiedDocFindNullTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class UnifiedDocFindNullTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocFindNullTagTest")!!
        assertNull("Should return null for null tag", docHelper.findDocByTag(psiClass, null))
    }

    fun testFindDocByTagReturnsNullForMissingTag() = runBlocking {
        loadFile("helper/UnifiedDocFindMissingTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class UnifiedDocFindMissingTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocFindMissingTagTest")!!
        assertNull("Should return null for missing tag", docHelper.findDocByTag(psiClass, "deprecated"))
    }

    // --- findDocsByTag ---

    fun testFindDocsByTagReturnsMultipleValues() = runBlocking {
        loadFile("helper/UnifiedDocMultiTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @see User
             * @see Account
             */
            public class UnifiedDocMultiTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocMultiTagTest")!!
        val seeDocs = docHelper.findDocsByTag(psiClass, "see")
        assertNotNull("Should find @see values", seeDocs)
        assertEquals("Should have 2 @see tags", 2, seeDocs!!.size)
    }

    fun testFindDocsByTagReturnsNullForNullElement() = runBlocking {
        assertNull("Should return null for null element", docHelper.findDocsByTag(null, "see"))
    }

    fun testFindDocsByTagReturnsNullForNullTag() = runBlocking {
        loadFile("helper/UnifiedDocMultiNullTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @see User
             */
            public class UnifiedDocMultiNullTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocMultiNullTagTest")!!
        assertNull("Should return null for null tag", docHelper.findDocsByTag(psiClass, null))
    }

    fun testFindDocsByTagReturnsNullForMissingTag() = runBlocking {
        loadFile("helper/UnifiedDocMultiMissingTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class UnifiedDocMultiMissingTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocMultiMissingTagTest")!!
        assertNull("Should return null for missing tag", docHelper.findDocsByTag(psiClass, "see"))
    }

    // --- findDocsByTagAndName ---

    fun testFindDocsByTagAndName() = runBlocking {
        loadFile("helper/UnifiedParamTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @param name the user name
             * @param age the user age
             */
            public class UnifiedParamTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedParamTest")!!
        val nameDoc = docHelper.findDocsByTagAndName(psiClass, "param", "name")
        assertNotNull("Should find @param name", nameDoc)
        assertTrue("Param doc should contain 'user name'", nameDoc!!.contains("user name"))
    }

    fun testFindDocsByTagAndNameReturnsNullForNullElement() = runBlocking {
        assertNull("Should return null for null element",
            docHelper.findDocsByTagAndName(null, "param", "name"))
    }

    fun testFindDocsByTagAndNameReturnsNullForMissingName() = runBlocking {
        loadFile("helper/UnifiedParamMissingTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @param name the user name
             */
            public class UnifiedParamMissingTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedParamMissingTest")!!
        val ageDoc = docHelper.findDocsByTagAndName(psiClass, "param", "age")
        assertNull("Should return null for missing param name", ageDoc)
    }

    fun testFindDocsByTagAndNameMatchesNameWithTabSeparator() = runBlocking {
        loadFile("helper/UnifiedParamTabTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @param name	the user name
             */
            public class UnifiedParamTabTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedParamTabTest")!!
        val nameDoc = docHelper.findDocsByTagAndName(psiClass, "param", "name")
        assertNotNull("Should find @param name with tab separator", nameDoc)
    }

    // --- getAttrOfDocComment ---

    fun testGetAttrOfDocCommentReturnsDescription() = runBlocking {
        loadFile("helper/UnifiedDocAttrTest.java", """
            package com.test.helper;
            /**
             * This is a test class description.
             */
            public class UnifiedDocAttrTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocAttrTest")!!
        val desc = docHelper.getAttrOfDocComment(psiClass)
        assertNotNull("Should find doc description", desc)
        assertTrue("Description should contain 'test class'", desc!!.contains("test class"))
    }

    fun testGetAttrOfDocCommentReturnsNullForNoDoc() = runBlocking {
        loadFile("helper/UnifiedNoDocTest.java", """
            package com.test.helper;
            public class UnifiedNoDocTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedNoDocTest")!!
        val desc = docHelper.getAttrOfDocComment(psiClass)
        assertNull("Should return null for class without doc comment", desc)
    }

    fun testGetAttrOfDocCommentReturnsNullForNullElement() = runBlocking {
        assertNull("Should return null for null element", docHelper.getAttrOfDocComment(null))
    }

    // --- getTagMapOfDocComment ---

    fun testGetTagMapOfDocCommentReturnsTags() = runBlocking {
        loadFile("helper/UnifiedDocTagMapTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             * @version 1.0
             */
            public class UnifiedDocTagMapTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocTagMapTest")!!
        val tagMap = docHelper.getTagMapOfDocComment(psiClass)
        assertNotNull("Should return tag map", tagMap)
        assertTrue("Tag map should contain 'author'", tagMap.containsKey("author"))
        assertTrue("Tag map should contain 'version'", tagMap.containsKey("version"))
    }

    fun testGetTagMapOfDocCommentReturnsEmptyForNullElement() = runBlocking {
        val tagMap = docHelper.getTagMapOfDocComment(null)
        assertTrue("Should return empty map for null element", tagMap.isEmpty())
    }

    fun testGetTagMapOfDocCommentReturnsEmptyForNoDoc() = runBlocking {
        loadFile("helper/UnifiedNoDocTagMapTest.java", """
            package com.test.helper;
            public class UnifiedNoDocTagMapTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedNoDocTagMapTest")!!
        val tagMap = docHelper.getTagMapOfDocComment(psiClass)
        assertTrue("Should return empty map for class without doc", tagMap.isEmpty())
    }

    // --- getSubTagMapOfDocComment ---

    fun testGetSubTagMapOfDocComment() = runBlocking {
        loadFile("helper/UnifiedSubTagTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @param name the user name
             * @param age the user age
             */
            public class UnifiedSubTagTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedSubTagTest")!!
        val paramMap = docHelper.getSubTagMapOfDocComment(psiClass, "param")
        assertNotNull("Should return param map", paramMap)
        assertTrue("Param map should contain 'name'", paramMap.containsKey("name"))
        assertTrue("Param map should contain 'age'", paramMap.containsKey("age"))
    }

    fun testGetSubTagMapOfDocCommentReturnsEmptyForNullElement() = runBlocking {
        val paramMap = docHelper.getSubTagMapOfDocComment(null, "param")
        assertTrue("Should return empty map for null element", paramMap.isEmpty())
    }

    fun testGetSubTagMapOfDocCommentReturnsEmptyForMissingTag() = runBlocking {
        loadFile("helper/UnifiedSubTagMissingTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class UnifiedSubTagMissingTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedSubTagMissingTest")!!
        val paramMap = docHelper.getSubTagMapOfDocComment(psiClass, "param")
        assertTrue("Should return empty map for missing tag", paramMap.isEmpty())
    }

    // --- getEolComment ---

    fun testGetEolCommentReturnsComment() = runBlocking {
        loadFile("helper/UnifiedEolCommentTest.java", """
            package com.test.helper;
            public class UnifiedEolCommentTest {
                private String name; // user name
            }
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedEolCommentTest")!!
        val field = psiClass.findFieldByName("name", false)!!
        val eolComment = docHelper.getEolComment(field)
        assertNotNull("Should find EOL comment", eolComment)
        assertTrue("EOL comment should contain 'user name'", eolComment!!.contains("user name"))
    }

    // --- getAttrOfField ---

    fun testGetAttrOfFieldWithDocComment() = runBlocking {
        loadFile("helper/UnifiedFieldDocTest.java", """
            package com.test.helper;
            public class UnifiedFieldDocTest {
                /** The user name. */
                private String name;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedFieldDocTest")!!
        val field = psiClass.findFieldByName("name", false)!!
        val attr = docHelper.getAttrOfField(field)
        assertNotNull("Should find field attribute", attr)
        assertTrue("Field attribute should contain 'user name'", attr!!.contains("user name"))
    }

    fun testGetAttrOfFieldWithEolComment() = runBlocking {
        loadFile("helper/UnifiedFieldEolTest.java", """
            package com.test.helper;
            public class UnifiedFieldEolTest {
                private String name; // user name
            }
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedFieldEolTest")!!
        val field = psiClass.findFieldByName("name", false)!!
        val attr = docHelper.getAttrOfField(field)
        assertNotNull("Should find field attribute from EOL comment", attr)
        assertTrue("Field attribute should contain 'user name'", attr!!.contains("user name"))
    }

    // --- getDocCommentContent ---

    fun testGetDocCommentContent() = runBlocking {
        loadFile("helper/UnifiedDocContentTest.java", """
            package com.test.helper;
            /**
             * This is a test description.
             * @author TestAuthor
             */
            public class UnifiedDocContentTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedDocContentTest")!!
        val docComment = (psiClass as com.intellij.psi.PsiDocCommentOwner).docComment
        assertNotNull("Class should have a doc comment", docComment)
        val content = docHelper.getDocCommentContent(docComment!!)
        assertNotNull("Should return doc comment content", content)
        assertTrue("Content should contain 'test description'", content!!.contains("test description"))
    }

    // --- method-level doc comment tests ---

    fun testHasTagOnMethod() = runBlocking {
        loadFile("helper/UnifiedMethodDocTest.java", """
            package com.test.helper;
            public class UnifiedMethodDocTest {
                /**
                 * Gets the user name.
                 * @return the user name
                 */
                public String getName() { return null; }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedMethodDocTest")!!
        val method = findMethod(psiClass, "getName")!!
        assertTrue("Should find @return tag on method", docHelper.hasTag(method, "return"))
    }

    fun testFindDocByTagOnMethod() = runBlocking {
        loadFile("helper/UnifiedMethodFindTagTest.java", """
            package com.test.helper;
            public class UnifiedMethodFindTagTest {
                /**
                 * Gets the user name.
                 * @return the user name
                 */
                public String getName() { return null; }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedMethodFindTagTest")!!
        val method = findMethod(psiClass, "getName")!!
        val returnDoc = docHelper.findDocByTag(method, "return")
        assertNotNull("Should find @return value on method", returnDoc)
        assertTrue("Return doc should contain 'user name'", returnDoc!!.contains("user name"))
    }

    fun testGetAttrOfDocCommentOnMethod() = runBlocking {
        loadFile("helper/UnifiedMethodAttrTest.java", """
            package com.test.helper;
            public class UnifiedMethodAttrTest {
                /**
                 * Gets the user name.
                 * @return the user name
                 */
                public String getName() { return null; }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.helper.UnifiedMethodAttrTest")!!
        val method = findMethod(psiClass, "getName")!!
        val desc = docHelper.getAttrOfDocComment(method)
        assertNotNull("Should find method doc description", desc)
        assertTrue("Method description should contain 'Gets the user name'", desc!!.contains("Gets the user name"))
    }

    // --- getInstance ---

    fun testGetInstanceReturnsSameInstance() {
        val instance1 = UnifiedDocHelper.getInstance(project)
        val instance2 = UnifiedDocHelper.getInstance(project)
        assertSame("getInstance should return the same service instance", instance1, instance2)
    }
}
