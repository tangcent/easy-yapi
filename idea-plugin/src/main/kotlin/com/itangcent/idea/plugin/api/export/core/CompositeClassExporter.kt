package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.utils.stream
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.callWithBoundary
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.spi.SpiCompositeLoader
import kotlin.reflect.KClass

@Singleton
class CompositeClassExporter : ClassExporter {

    @Inject
    private lateinit var actionContext: ActionContext

    private val subClassExporters: Array<ClassExporter> by lazy {
        SpiCompositeLoader.load(actionContext)
    }

    override fun support(docType: KClass<*>): Boolean {
        return this.subClassExporters.any { it.support(docType) }
    }

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        return this.subClassExporters.any {
            it.export(cls, docHandle)
        }
    }
}