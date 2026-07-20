package com.itangcent.easyapi.core.psi.helper

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class UnifiedDocHelperSourceResolutionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var docHelper: UnifiedDocHelper

    override fun setUp() {
        super.setUp()
        docHelper = UnifiedDocHelper.getInstance(project)
    }

    // --- getAttrOfDocComment: source-resolved class doc ---

    fun testGetAttrOfDocCommentForSourceClass() = runBlocking {
        loadFile("sourceres/DocumentedClass.java", """
            package com.test.sourceres;
            /**
             * This is a documented class.
             */
            public class DocumentedClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.DocumentedClass")!!
        val desc = docHelper.getAttrOfDocComment(psiClass)
        assertNotNull("Should find doc description for source class", desc)
        assertTrue("Description should contain 'documented class'", desc!!.contains("documented class"))
    }

    // --- getAttrOfDocComment: source-resolved field doc ---

    fun testGetAttrOfDocCommentForDocumentedField() = runBlocking {
        loadFile("sourceres/FieldDocClass.java", """
            package com.test.sourceres;
            /**
             * A class with documented fields.
             */
            public class FieldDocClass {
                /** The user identifier. */
                private String userId;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.FieldDocClass")!!
        val field = psiClass.findFieldByName("userId", false)!!
        val desc = docHelper.getAttrOfDocComment(field)
        assertNotNull("Should find doc description for field", desc)
        assertTrue("Description should contain 'user identifier'", desc!!.contains("user identifier"))
    }

    // --- getAttrOfDocComment: source-resolved method doc ---

    fun testGetAttrOfDocCommentForDocumentedMethod() = runBlocking {
        loadFile("sourceres/MethodDocClass.java", """
            package com.test.sourceres;
            /**
             * A class with documented methods.
             */
            public class MethodDocClass {
                /**
                 * Retrieves the account balance.
                 * @return the balance
                 */
                public double getBalance() { return 0.0; }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.MethodDocClass")!!
        val method = findMethod(psiClass, "getBalance")!!
        val desc = docHelper.getAttrOfDocComment(method)
        assertNotNull("Should find doc description for method", desc)
        assertTrue("Description should contain 'Retrieves the account balance'", desc!!.contains("Retrieves the account balance"))
    }

    // --- hasTag: source-resolved element ---

    fun testHasTagOnSourceClass() = runBlocking {
        loadFile("sourceres/TaggedClass.java", """
            package com.test.sourceres;
            /**
             * A tagged class.
             * @author TestAuthor
             * @version 2.0
             */
            public class TaggedClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.TaggedClass")!!
        assertTrue("Should find @author tag on source class", docHelper.hasTag(psiClass, "author"))
        assertTrue("Should find @version tag on source class", docHelper.hasTag(psiClass, "version"))
        assertFalse("Should not find @deprecated tag", docHelper.hasTag(psiClass, "deprecated"))
    }

    // --- findDocByTag: source-resolved element ---

    fun testFindDocByTagOnSourceClass() = runBlocking {
        loadFile("sourceres/TagValueClass.java", """
            package com.test.sourceres;
            /**
             * A class with tag values.
             * @author Jane Smith
             */
            public class TagValueClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.TagValueClass")!!
        val author = docHelper.findDocByTag(psiClass, "author")
        assertNotNull("Should find @author value on source class", author)
        assertTrue("Author should contain 'Jane Smith'", author!!.contains("Jane Smith"))
    }

    // --- findDocsByTag: source-resolved element ---

    fun testFindDocsByTagOnSourceClass() = runBlocking {
        loadFile("sourceres/MultiTagClass.java", """
            package com.test.sourceres;
            /**
             * A class with multiple @see tags.
             * @see UserDTO
             * @see AccountDTO
             */
            public class MultiTagClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.MultiTagClass")!!
        val seeDocs = docHelper.findDocsByTag(psiClass, "see")
        assertNotNull("Should find @see values on source class", seeDocs)
        assertEquals("Should have 2 @see tags", 2, seeDocs!!.size)
    }

    // --- getTagMapOfDocComment: source-resolved element ---

    fun testGetTagMapOfDocCommentOnSourceClass() = runBlocking {
        loadFile("sourceres/TagMapClass.java", """
            package com.test.sourceres;
            /**
             * A class with tag map.
             * @author DevTeam
             * @since 1.0
             */
            public class TagMapClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.TagMapClass")!!
        val tagMap = docHelper.getTagMapOfDocComment(psiClass)
        assertTrue("Tag map should contain 'author'", tagMap.containsKey("author"))
        assertTrue("Tag map should contain 'since'", tagMap.containsKey("since"))
    }

    // --- getSubTagMapOfDocComment: source-resolved element ---

    fun testGetSubTagMapOfDocCommentOnSourceMethod() = runBlocking {
        loadFile("sourceres/SubTagClass.java", """
            package com.test.sourceres;
            /**
             * A class with param tags.
             */
            public class SubTagClass {
                /**
                 * Creates a new user.
                 * @param name the user name
                 * @param email the user email
                 */
                public void createUser(String name, String email) {}
            }
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.SubTagClass")!!
        val method = findMethod(psiClass, "createUser")!!
        val paramMap = docHelper.getSubTagMapOfDocComment(method, "param")
        assertTrue("Param map should contain 'name'", paramMap.containsKey("name"))
        assertTrue("Param map should contain 'email'", paramMap.containsKey("email"))
        assertTrue("Name param should contain 'user name'", paramMap["name"]!!.contains("user name"))
    }

    // --- getAttrOfField: doc comment on field ---

    fun testGetAttrOfFieldWithDocComment() = runBlocking {
        loadFile("sourceres/FieldAttrClass.java", """
            package com.test.sourceres;
            /**
             * A class with field attributes.
             */
            public class FieldAttrClass {
                /** The product code. */
                private String productCode;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.FieldAttrClass")!!
        val field = psiClass.findFieldByName("productCode", false)!!
        val attr = docHelper.getAttrOfField(field)
        assertNotNull("Should find field attribute from doc comment", attr)
        assertTrue("Field attribute should contain 'product code'", attr!!.contains("product code"))
    }

    // --- getAttrOfField: EOL comment on field ---

    fun testGetAttrOfFieldWithEolComment() = runBlocking {
        loadFile("sourceres/EolFieldClass.java", """
            package com.test.sourceres;
            /**
             * A class with EOL comments.
             */
            public class EolFieldClass {
                private String productCode; // the product code
            }
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.EolFieldClass")!!
        val field = psiClass.findFieldByName("productCode", false)!!
        val attr = docHelper.getAttrOfField(field)
        assertNotNull("Should find field attribute from EOL comment", attr)
        assertTrue("Field attribute should contain 'product code'", attr!!.contains("product code"))
    }

    // --- getAttrOfDocComment: null element ---

    fun testGetAttrOfDocCommentReturnsNullForNullElement() = runBlocking {
        assertNull("Should return null for null element", docHelper.getAttrOfDocComment(null))
    }

    // --- resolveDocComment via hasTag for method ---

    fun testHasTagOnSourceMethod() = runBlocking {
        loadFile("sourceres/MethodTagClass.java", """
            package com.test.sourceres;
            public class MethodTagClass {
                /**
                 * Validates the input.
                 * @param input the input to validate
                 * @return true if valid
                 * @throws IllegalArgumentException if invalid
                 */
                public boolean validate(String input) { return true; }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.MethodTagClass")!!
        val method = findMethod(psiClass, "validate")!!
        assertTrue("Should find @param tag on method", docHelper.hasTag(method, "param"))
        assertTrue("Should find @return tag on method", docHelper.hasTag(method, "return"))
        assertTrue("Should find @throws tag on method", docHelper.hasTag(method, "throws"))
    }

    // --- findDocByTag on method ---

    fun testFindDocByTagOnSourceMethod() = runBlocking {
        loadFile("sourceres/MethodTagValueClass.java", """
            package com.test.sourceres;
            public class MethodTagValueClass {
                /**
                 * Calculates the total.
                 * @return the total amount
                 */
                public double calculateTotal() { return 0.0; }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.sourceres.MethodTagValueClass")!!
        val method = findMethod(psiClass, "calculateTotal")!!
        val returnDoc = docHelper.findDocByTag(method, "return")
        assertNotNull("Should find @return value on method", returnDoc)
        assertTrue("Return doc should contain 'total amount'", returnDoc!!.contains("total amount"))
    }
}
