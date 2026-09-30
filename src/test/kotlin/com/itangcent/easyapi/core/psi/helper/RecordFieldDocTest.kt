package com.itangcent.easyapi.core.psi.helper

import com.itangcent.easyapi.core.psi.DefaultPsiClassHelper
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Regression tests for issue #1472: Java `record` component Javadoc (`@param` tags)
 * must be resolved as the field description during export.
 *
 * Java records document their components via `@param` tags on the record class
 * (not per-field doc comments):
 *
 * ```java
 * public record LoginVO(String token) {}
 * ```
 * with `@param token the auth token` inside the class Javadoc above it.
 *
 * The synthetic backing field (a `LightRecordField` augment in `psiClass.fields`)
 * carries no doc comment of its own, so without record-aware resolution every
 * channel (YApi, Markdown, ...) renders record fields without descriptions.
 */
class RecordFieldDocTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var docHelper: UnifiedDocHelper
    private lateinit var metadataResolver: DocMetadataResolver

    override fun setUp() {
        super.setUp()
        docHelper = UnifiedDocHelper.getInstance(project)
        metadataResolver = DocMetadataResolver.getInstance(project)
        loadFile(
            "model/LoginRecordVO.java",
            """
            package com.itangcent.model;
            /**
             * Login view object.
             * @param token the auth token
             * @param roleId the role id
             * @param roleName multi-line role name:
             *         second line of role name
             */
            public record LoginRecordVO(String token, Long roleId, String roleName, Integer level) {
            }
            """.trimIndent()
        )
        loadFile(
            "model/UndocumentedRecordVO.java",
            """
            package com.itangcent.model;
            /**
             * A record without @param tags.
             */
            public record UndocumentedRecordVO(String token) {
            }
            """.trimIndent()
        )
    }

    override fun createConfigReader() = TestConfigReader.empty(project)

    // --- UnifiedDocHelper.getAttrOfField ---

    fun testGetAttrOfFieldResolvesRecordParamDoc() = runTest {
        val psiClass = findClass("com.itangcent.model.LoginRecordVO")!!
        val field = psiClass.findFieldByName("token", false)!!
        assertEquals("the auth token", docHelper.getAttrOfField(field))
    }

    fun testGetAttrOfFieldResolvesRecordParamDocForSecondComponent() = runTest {
        val psiClass = findClass("com.itangcent.model.LoginRecordVO")!!
        val field = psiClass.findFieldByName("roleId", false)!!
        assertEquals("the role id", docHelper.getAttrOfField(field))
    }

    fun testGetAttrOfFieldResolvesMultiLineRecordParamDoc() = runTest {
        val psiClass = findClass("com.itangcent.model.LoginRecordVO")!!
        val field = psiClass.findFieldByName("roleName", false)!!
        val doc = docHelper.getAttrOfField(field)!!
        assertTrue("Should contain first line", doc.contains("multi-line role name:"))
        assertTrue("Should contain second line", doc.contains("second line of role name"))
    }

    fun testGetAttrOfFieldReturnsNullForUndocumentedRecordComponent() = runTest {
        val psiClass = findClass("com.itangcent.model.UndocumentedRecordVO")!!
        val field = psiClass.findFieldByName("token", false)!!
        // No @param tag for the component; the record class's own description
        // ("A record without @param tags.") must NOT leak as the field doc.
        assertNull(docHelper.getAttrOfField(field))
    }

    fun testRecordFieldsAreEnumeratedAsFields() = runTest {
        val psiClass = findClass("com.itangcent.model.LoginRecordVO")!!
        assertTrue("Record class should be recognized as record", psiClass.isRecord)
        // Components enter field iteration via the RecordAugmentProvider light fields.
        assertNotNull("token should be visible as a field", psiClass.findFieldByName("token", false))
        assertNotNull("roleId should be visible as a field", psiClass.findFieldByName("roleId", false))
    }

    // --- UnifiedDocHelper.getAttrOfDocComment (record component element) ---

    fun testGetAttrOfDocCommentOnRecordComponent() = runTest {
        val psiClass = findClass("com.itangcent.model.LoginRecordVO")!!
        val component = psiClass.recordComponents.first { it.name == "token" }
        assertEquals("the auth token", docHelper.getAttrOfDocComment(component))
    }

    // --- DocMetadataResolver.resolveFieldDoc (pipeline level) ---

    fun testResolveFieldDocOnRecordField() = runTest {
        val psiClass = findClass("com.itangcent.model.LoginRecordVO")!!
        val field = psiClass.findFieldByName("token", false)!!
        assertEquals("the auth token", metadataResolver.resolveFieldDoc(field))
    }

    // --- DefaultPsiClassHelper.buildObjectModel (what channels render) ---

    fun testBuildObjectModelIncludesRecordFieldComments() = runTest {
        val psiClass = findClass("com.itangcent.model.LoginRecordVO")!!
        val model = DefaultPsiClassHelper.getInstance(project).buildObjectModel(psiClass)
            as ObjectModel.Object
        assertEquals("the auth token", model.fields["token"]?.comment)
        assertEquals("the role id", model.fields["roleId"]?.comment)
    }
}
