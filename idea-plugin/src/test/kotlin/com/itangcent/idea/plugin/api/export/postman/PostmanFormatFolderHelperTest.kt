package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.plugin.api.export.core.FormatFolderHelper
import com.itangcent.idea.psi.PsiClassResource
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import org.junit.jupiter.api.condition.OS

/**
 * Test case of [PostmanFormatFolderHelper]
 * Test case of rule: [com.itangcent.idea.plugin.api.export.postman.PostmanExportRuleKeys.CLASS_POST_PRE_REQUEST]
 * Test case of rule: [com.itangcent.idea.plugin.api.export.postman.PostmanExportRuleKeys.CLASS_POST_TEST]
 */
internal class PostmanFormatFolderHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var formatFolderHelper: FormatFolderHelper

    @Inject
    private lateinit var duckTypeHelper: DuckTypeHelper

    private lateinit var userCtrlPsiClass: PsiClass

    override fun customConfig(): String {
        //language=Properties
        return """
            # read folder name from tag `folder`
            folder.name=#folder
            
            postman.class.prerequest=```
            pm.environment.set("token", "123456");
            ```
            
            postman.class.test=```
            pm.test("Successful POST request", function () {
            pm.expect(pm.response.code).to.be.oneOf([201,202]);
            });
            ```
        """.trimIndent()
    }

    override fun setUp() {
        super.setUp()
        userCtrlPsiClass = createClass("api/UserCtrl.java")!!
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(FormatFolderHelper::class) {
            it.with(PostmanFormatFolderHelper::class)
        }
    }

    override fun shouldRunTest(): Boolean {
        //not run in windows
        return !OS.WINDOWS.isCurrentOs
    }

    fun testResolveFolder() {
        //test for PsiClass & PsiMethod
        formatFolderHelper.resolveFolder(userCtrlPsiClass).let {
            assertEquals(
                Folder(
                    "apis about user", "apis about user\n" +
                            "access user info"
                ), it
            )
            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", it.getExt(PostmanExportRuleKeys.POST_TEST.name())
            )
        }
        formatFolderHelper.resolveFolder(userCtrlPsiClass.methods[0]).let {
            assertEquals(
                Folder(
                    "apis about user", "apis about user\n" +
                            "access user info"
                ), it
            )
            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", it.getExt(PostmanExportRuleKeys.POST_TEST.name())
            )
        }
        formatFolderHelper.resolveFolder(userCtrlPsiClass.methods[1]).let {
            assertEquals(Folder("update-apis", ""), it)
            assertNull(it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name()))
            assertNull(it.getExt(PostmanExportRuleKeys.POST_TEST.name()))
        }

        //test for ExplicitClass & ExplicitMethod
        val explicitClass = duckTypeHelper.explicit(userCtrlPsiClass)
        formatFolderHelper.resolveFolder(explicitClass).let {
            assertEquals(
                Folder(
                    "apis about user", "apis about user\n" +
                            "access user info"
                ), it
            )
            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", it.getExt(PostmanExportRuleKeys.POST_TEST.name())
            )
        }
        formatFolderHelper.resolveFolder(explicitClass.methods()[0]).let {
            assertEquals(
                Folder(
                    "apis about user", "apis about user\n" +
                            "access user info"
                ), it
            )
            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", it.getExt(PostmanExportRuleKeys.POST_TEST.name())
            )
        }
        formatFolderHelper.resolveFolder(explicitClass.methods()[1]).let {
            assertEquals(Folder("update-apis", ""), it)
            assertNull(it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name()))
            assertNull(it.getExt(PostmanExportRuleKeys.POST_TEST.name()))
        }

        //test for PsiClassResource & PsiMethodResource
        formatFolderHelper.resolveFolder(PsiClassResource(userCtrlPsiClass)).let {
            assertEquals(
                Folder(
                    "apis about user", "apis about user\n" +
                            "access user info"
                ), it
            )
            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", it.getExt(PostmanExportRuleKeys.POST_TEST.name())
            )
        }
        formatFolderHelper.resolveFolder(PsiMethodResource(userCtrlPsiClass.methods[0], userCtrlPsiClass)).let {
            assertEquals(
                Folder(
                    "apis about user", "apis about user\n" +
                            "access user info"
                ), it
            )
            assertEquals(
                "pm.environment.set(\"token\", \"123456\");",
                it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name())
            )
            assertEquals(
                "pm.test(\"Successful POST request\", function () {\n" +
                        "pm.expect(pm.response.code).to.be.oneOf([201,202]);\n" +
                        "});", it.getExt(PostmanExportRuleKeys.POST_TEST.name())
            )
        }
        formatFolderHelper.resolveFolder(PsiMethodResource(userCtrlPsiClass.methods[1], userCtrlPsiClass)).let {
            assertEquals(Folder("update-apis", ""), it)
            assertNull(it.getExt(PostmanExportRuleKeys.POST_PRE_REQUEST.name()))
            assertNull(it.getExt(PostmanExportRuleKeys.POST_TEST.name()))
        }
    }
}