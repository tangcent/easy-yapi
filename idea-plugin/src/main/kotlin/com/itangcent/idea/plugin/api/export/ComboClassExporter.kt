package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.name.Named
import com.intellij.util.containers.stream
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.util.reduceSafely
import kotlin.reflect.KClass

class ComboClassExporter : ClassExporter, Worker {

    @Inject
    @Named("AVAILABLE_CLASS_EXPORTER")
    private var classExporters: Array<*>? = null

    @Inject
    private var actionContext: ActionContext? = null

    private var subClassExporters: Array<ClassExporter>? = null

    @PostConstruct
    fun init() {
        subClassExporters = classExporters
                ?.map { it as KClass<*> }
                ?.map { actionContext!!.instance(it) }
                ?.map { it as ClassExporter }
                ?.toTypedArray()
    }

    override fun support(docType: KClass<*>): Boolean {
        return this.subClassExporters?.stream()?.map { it.support(docType) }?.anyMatch { it } ?: false
    }

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        return this.subClassExporters?.stream()?.map { it.export(cls, docHandle) }?.anyMatch { it } ?: false
    }

    override fun status(): WorkerStatus {
        return this.subClassExporters
                ?.filter { it is Worker }
                ?.map { it as Worker }
                ?.map { it.status() }
                ?.reduceSafely(WorkerStatus::and)
                ?: WorkerStatus.FREE
    }

    override fun waitCompleted() {
        this.subClassExporters
                ?.filter { it is Worker }
                ?.map { it as Worker }
                ?.forEach { it.waitCompleted() }
    }

    override fun cancel() {
        this.subClassExporters
                ?.filter { it is Worker }
                ?.map { it as Worker }
                ?.forEach { it.cancel() }
    }
}