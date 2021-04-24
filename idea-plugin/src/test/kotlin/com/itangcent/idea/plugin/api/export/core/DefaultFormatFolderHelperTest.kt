package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.itangcent.idea.plugin.api.export.core.DefaultFormatFolderHelper
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.plugin.api.export.core.FormatFolderHelper
import com.itangcent.idea.psi.PsiClassResource
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import junit.framework.Assert

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

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(FormatFolderHelper::class) {
            it.with(DefaultFormatFolderHelper::class)
        }
    }

    fun testResolveFolder() {

        //test of PsiClass & PsiMethod
        Assert.assertEquals(Folder("apis about user", "apis about user\n" +
                "access user info"), formatFolderHelper.resolveFolder(userCtrlPsiClass))
        Assert.assertEquals(Folder("apis about user", "apis about user\n" +
                "access user info"), formatFolderHelper.resolveFolder(userCtrlPsiClass.methods[0]))
        Assert.assertEquals(Folder("update-apis", ""), formatFolderHelper.resolveFolder(userCtrlPsiClass.methods[1]))

        //test of ExplicitClass & ExplicitMethod
        val explicitClass = duckTypeHelper.explicit(userCtrlPsiClass)
        Assert.assertEquals(Folder("apis about user", "apis about user\n" +
                "access user info"), formatFolderHelper.resolveFolder(explicitClass))
        Assert.assertEquals(Folder("apis about user", "apis about user\n" +
                "access user info"), formatFolderHelper.resolveFolder(explicitClass.methods()[0]))
        Assert.assertEquals(Folder("update-apis", ""), formatFolderHelper.resolveFolder(explicitClass.methods()[1]))

        //test of PsiClassResource & PsiMethodResource
        Assert.assertEquals(Folder("apis about user", "apis about user\n" +
                "access user info"), formatFolderHelper.resolveFolder(PsiClassResource(userCtrlPsiClass)))
        Assert.assertEquals(Folder("apis about user", "apis about user\n" +
                "access user info"), formatFolderHelper.resolveFolder(PsiMethodResource(userCtrlPsiClass.methods[0], userCtrlPsiClass)))
        Assert.assertEquals(Folder("update-apis", ""), formatFolderHelper.resolveFolder(PsiMethodResource(userCtrlPsiClass.methods[1], userCtrlPsiClass)))


    }
}