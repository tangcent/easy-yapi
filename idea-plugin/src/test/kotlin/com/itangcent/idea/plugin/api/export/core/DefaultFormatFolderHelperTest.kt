package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.idea.psi.PsiClassResource
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case of [DefaultFormatFolderHelper]
 * Test case of rule: [com.itangcent.idea.plugin.api.export.ClassExportRuleKeys.API_FOLDER]
 */
internal class DefaultFormatFolderHelperTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var formatFolderHelper: FormatFolderHelper

    @Inject
    private lateinit var duckTypeHelper: DuckTypeHelper

    private lateinit var userCtrlPsiClass: PsiClass

    override fun customConfig(): String {
        //language=Properties
        return "# read folder name from tag `folder`\nfolder.name=#folder"
    }

    override fun setUp() {
        super.setUp()
        userCtrlPsiClass = createClass("api/UserCtrl.java")!!
    }

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(FormatFolderHelper::class) {
            it.with(DefaultFormatFolderHelper::class)
        }
    }

    fun testResolveFolder() {

        //test for PsiClass & PsiMethod
        assertEquals(
            Folder(
                "apis about user", "apis about user\n" +
                        "access user info"
            ), formatFolderHelper.resolveFolder(userCtrlPsiClass)
        )
        assertEquals(
            Folder(
                "apis about user", "apis about user\n" +
                        "access user info"
            ), formatFolderHelper.resolveFolder(userCtrlPsiClass.methods[0])
        )
        assertEquals(Folder("update-apis", ""), formatFolderHelper.resolveFolder(userCtrlPsiClass.methods[1]))

        //test for ExplicitClass & ExplicitMethod
        val explicitClass = duckTypeHelper.explicit(userCtrlPsiClass)
        assertEquals(
            Folder(
                "apis about user", "apis about user\n" +
                        "access user info"
            ), formatFolderHelper.resolveFolder(explicitClass)
        )
        assertEquals(
            Folder(
                "apis about user", "apis about user\n" +
                        "access user info"
            ), formatFolderHelper.resolveFolder(explicitClass.methods()[0])
        )
        assertEquals(Folder("update-apis", ""), formatFolderHelper.resolveFolder(explicitClass.methods()[1]))

        //test for PsiClassResource & PsiMethodResource
        assertEquals(
            Folder(
                "apis about user", "apis about user\n" +
                        "access user info"
            ), formatFolderHelper.resolveFolder(PsiClassResource(userCtrlPsiClass))
        )
        assertEquals(
            Folder(
                "apis about user", "apis about user\n" +
                        "access user info"
            ), formatFolderHelper.resolveFolder(PsiMethodResource(userCtrlPsiClass.methods[0], userCtrlPsiClass))
        )
        assertEquals(
            Folder("update-apis", ""),
            formatFolderHelper.resolveFolder(PsiMethodResource(userCtrlPsiClass.methods[1], userCtrlPsiClass))
        )


    }
}