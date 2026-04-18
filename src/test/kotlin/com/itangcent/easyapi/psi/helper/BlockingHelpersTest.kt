package com.itangcent.easyapi.psi.helper

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class BlockingHelpersTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var blockingDocHelper: BlockingDocHelper

    override fun setUp() {
        super.setUp()
        val docHelper = StandardDocHelper.getInstance(project)
        blockingDocHelper = BlockingDocHelper(docHelper)
    }

    fun testBlockingDocHelperHasTag() {
        loadFile("helper/BlockingDocTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author TestAuthor
             */
            public class BlockingDocTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.BlockingDocTest")!!
        assertTrue("Should find @author tag", blockingDocHelper.hasTag(psiClass, "author"))
    }

    fun testBlockingDocHelperFindDocByTag() {
        loadFile("helper/BlockingDocFindTest.java", """
            package com.test.helper;
            /**
             * Test class.
             * @author John Doe
             */
            public class BlockingDocFindTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.BlockingDocFindTest")!!
        val author = blockingDocHelper.findDocByTag(psiClass, "author")
        assertNotNull("Should find @author value", author)
    }

    fun testBlockingDocHelperGetAttrOfDocComment() {
        loadFile("helper/BlockingDocAttrTest.java", """
            package com.test.helper;
            /**
             * This is a test description.
             */
            public class BlockingDocAttrTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.BlockingDocAttrTest")!!
        val desc = blockingDocHelper.getAttrOfDocComment(psiClass)
        assertNotNull("Should find doc description", desc)
    }

    fun testBlockingAnnotationHelperCreation() {
        val annotationHelper = UnifiedAnnotationHelper()
        val blockingAnnotationHelper = BlockingAnnotationHelper(annotationHelper)
        assertNotNull("BlockingAnnotationHelper should be created", blockingAnnotationHelper)
    }

    fun testBlockingDocHelperCreation() {
        val docHelper = StandardDocHelper.getInstance(project)
        val blockingDocHelper = BlockingDocHelper(docHelper)
        assertNotNull("BlockingDocHelper should be created", blockingDocHelper)
    }
}
