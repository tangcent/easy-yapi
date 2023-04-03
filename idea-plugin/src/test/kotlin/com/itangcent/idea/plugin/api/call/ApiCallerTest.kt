package com.itangcent.idea.plugin.api.call

import com.google.inject.Inject
import com.intellij.psi.PsiFile
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.DocHandle
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import com.itangcent.test.assertLinesContain
import com.itangcent.test.mock
import com.itangcent.test.workAt
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import junit.framework.Assert
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

/**
 * Test case with [ApiCaller]
 */
internal abstract class ApiCallerTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    protected lateinit var apiCaller: ApiCaller

    private lateinit var testCtrlPsiFile: PsiFile

    protected lateinit var apiCallUI: ApiCallUI

    protected var requestListInUI: List<Request>? = null

    override fun beforeBind() {
        super.beforeBind()
        testCtrlPsiFile = loadFile("api/TestCtrl.java")!!
        LoggerCollector.getLog()
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)

        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
        builder.bindInstance(ClassExporter::class,
            mock<ClassExporter>()
                .also { classExporter ->
                    Mockito.`when`(classExporter.export(any(), any()))
                        .thenAnswer { invocationOnMock ->
                            val docHandle = invocationOnMock.getArgument<DocHandle>(1)
                            return@thenAnswer onExport(docHandle)
                        }
                })
        builder.mock(ApiCallUI::class) { apiCallUI ->
            this.apiCallUI = apiCallUI
            Mockito.`when`(apiCallUI.updateRequestList(any()))
                .thenAnswer {
                    requestListInUI = it.getArgument<List<Request>>(0)
                    return@thenAnswer null
                }
        }
        builder.workAt(testCtrlPsiFile)
    }

    open fun onExport(docHandle: DocHandle): Boolean {
        //NOP
        return true
    }

    class NoRequestBeFoundApiCallerTest : ApiCallerTest() {

        fun testShowCallWindow() {
            apiCaller.showCallWindow()
            actionContext.waitComplete()
            assertEquals(
                "[INFO]\tStart find apis...\n" +
                        "[INFO]\tNo api be found to call!\n",
                LoggerCollector.getLog().toUnixString()
            )
        }
    }

    class ExportFailedApiCallerTest : ApiCallerTest() {

        override fun onExport(docHandle: DocHandle): Boolean {
            throw RuntimeException("export time out")
        }

        fun testShowCallWindow() {
            apiCaller.showCallWindow()
            actionContext.waitComplete()
            assertLinesContain(
                ResultLoader.load(),
                LoggerCollector.getLog().replace(Regex("\\d"), "").toUnixString()
            )
        }
    }

    class SuccessApiCallerTest : ApiCallerTest() {

        private val requests: List<Request> = listOf(Request().also {
            it.name = "request1"
            it.path = URL.of("/a")
        }, Request().also {
            it.name = "request2"
            it.path = URL.of("/b")
        })

        override fun onExport(docHandle: DocHandle): Boolean {
            requests.forEach(docHandle)
            return true
        }

        fun testShowCallWindow() {
            apiCaller.showCallWindow()
            actionContext.waitComplete()
            assertEquals(
                "[INFO]\tStart find apis...\n",
                LoggerCollector.getLog().replace(Regex("\\d"), "").toUnixString()
            )
            assertEquals(requests, requestListInUI)
        }
    }

    class ReentrantApiCallerTest : ApiCallerTest() {

        private val requests: List<Request> = listOf(Request().also {
            it.name = "request1"
            it.path = URL.of("/a")
        }, Request().also {
            it.name = "request2"
            it.path = URL.of("/b")
        })

        override fun onExport(docHandle: DocHandle): Boolean {
            requests.forEach(docHandle)
            return true
        }

        fun testShowCallWindow() {
            apiCaller.showCallWindow()
            Thread.sleep(1000)
            actionContext.instance(ApiCaller::class).showCallWindow()
            actionContext.waitComplete()
            assertEquals(
                "[INFO]\tStart find apis...\n",
                LoggerCollector.getLog().replace(Regex("\\d"), "").toUnixString()
            )
            assertEquals(requests, requestListInUI)
            verify(apiCallUI, times(1)).focusUI()
            verify(apiCallUI, times(1)).showUI()
        }
    }
}