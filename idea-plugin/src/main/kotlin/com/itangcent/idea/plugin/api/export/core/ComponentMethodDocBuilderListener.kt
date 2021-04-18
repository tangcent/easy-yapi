package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.google.inject.name.Named
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Param
import com.itangcent.intellij.jvm.spi.ProxyBuilder
import kotlin.reflect.KClass

class ComponentMethodDocBuilderListener :
        MethodDocBuilderListener {

    @Inject
    @Named("AVAILABLE_METHOD_DOC_BUILDER_LISTENER")
    private lateinit var delegateClass: Array<*>

    private val delegate: MethodDocBuilderListener by lazy {
        buildDelegate()
    }

    private fun buildDelegate(): MethodDocBuilderListener {
        val proxyBuilder = ProxyBuilder(MethodDocBuilderListener::class)
        for (delegateClass in delegateClass) {
            proxyBuilder.addImplementClass(delegateClass as KClass<*>)
        }
        return proxyBuilder.buildProxy() as MethodDocBuilderListener
    }

    override fun setName(exportContext: ExportContext, methodDoc: MethodDoc, name: String) {
        delegate.setName(exportContext, methodDoc, name)
    }

    override fun appendDesc(exportContext: ExportContext, methodDoc: MethodDoc, desc: String?) {
        delegate.appendDesc(exportContext, methodDoc, desc)
    }

    override fun addParam(exportContext: ExportContext, methodDoc: MethodDoc, param: Param) {
        delegate.addParam(exportContext, methodDoc, param)
    }

    override fun setRet(exportContext: ExportContext, methodDoc: MethodDoc, ret: Any?) {
        delegate.setRet(exportContext, methodDoc, ret)
    }

    override fun appendRetDesc(exportContext: ExportContext, methodDoc: MethodDoc, retDesc: String?) {
        delegate.appendRetDesc(exportContext, methodDoc, retDesc)
    }

    override fun startProcessMethod(methodExportContext: MethodExportContext, methodDoc: MethodDoc) {
        delegate.startProcessMethod(methodExportContext, methodDoc)
    }

    override fun processCompleted(methodExportContext: MethodExportContext, methodDoc: MethodDoc) {
        delegate.processCompleted(methodExportContext, methodDoc)
    }
}