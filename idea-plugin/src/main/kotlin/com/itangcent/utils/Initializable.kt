package com.itangcent.utils

import com.itangcent.intellij.context.ActionContext

/**
 * Interface to be implemented by beans that need to react once all their properties
 * have been injected by [ActionContext]: e.g. to perform custom initialization,
 * or merely to check that all mandatory properties have been set.
 */
interface Initializable {

    fun init()
}