package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.plugin.settings.helper.PostmanWorkspaceChecker

/**
 * @author gcdd1993
 */
@Singleton
class PostmanWorkspaceCheckerSupport : PostmanWorkspaceChecker {
    @Inject
    private lateinit var postmanApiHelper: PostmanApiHelper

    override fun checkWorkspace(workspace: String): Boolean {
        return postmanApiHelper.getWorkspaceInfo(workspace) != null
    }
}