package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Inject
import com.itangcent.common.utils.filterAs
import com.itangcent.common.utils.reduceSafely
import com.itangcent.common.utils.stream
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.intellij.context.ActionContext
import com.itangcent.spi.SpiCompositeLoader
import kotlin.reflect.KClass

class CompositeClassExporter : ClassExporter, Worker {

    @Inject
    private lateinit var actionContext: ActionContext

    private val subClassExporters: Array<ClassExporter> by lazy {
        SpiCompositeLoader.load(actionContext)
    }

    override fun support(docType: KClass<*>): Boolean {
        return this.subClassExporters.stream().map { it.support(docType) }?.anyMatch { it } ?: false
    }

    override fun export(cls: Any, docHandle: DocHandle, completedHandle: CompletedHandle): Boolean {
        val ret = this.subClassExporters.stream().map {
            it.export(cls, docHandle)
        }?.anyMatch { it } ?: false
        completedHandle(cls)
        return ret;
    }

    override fun status(): WorkerStatus {
        return this.subClassExporters
            .stream()
            .filter { it is Worker }
            ?.map { it as Worker }
            ?.map { it.status() }
            ?.reduceSafely(WorkerStatus::and)
            ?: WorkerStatus.FREE
    }

    override fun waitCompleted() {
        this.subClassExporters
            .stream()
            .filterAs<Worker>()
            .forEach { it.waitCompleted() }
    }

    override fun cancel() {
        this.subClassExporters
            .stream()
            .filterAs<Worker>()
            .forEach { it.cancel() }
    }
}