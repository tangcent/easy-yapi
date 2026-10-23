package com.itangcent.easyapi.testFramework

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerOrReplaceServiceInstance
import com.itangcent.easyapi.core.logging.IdeaConsole
import com.itangcent.easyapi.core.logging.IdeaConsoleProvider
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

/**
 * Makes a mock the console of [project] until [parentDisposable] (usually the test's `testRootDisposable`)
 * is disposed; verify on the returned mock what the code under test wrote to the console.
 */
fun recordConsole(project: Project, parentDisposable: Disposable): IdeaConsole {
    val console = mock<IdeaConsole>()
    val provider = mock<IdeaConsoleProvider> { on { getConsole() } doReturn console }
    project.registerOrReplaceServiceInstance(IdeaConsoleProvider::class.java, provider, parentDisposable)
    return console
}
