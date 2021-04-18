package com.itangcent.idea.plugin.api.export.core

import com.google.inject.ImplementedBy
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Param
import com.itangcent.common.model.Request

@ImplementedBy(DefaultMethodDocBuilderListener::class)
interface MethodDocBuilderListener {

    fun setName(exportContext: ExportContext,
                methodDoc: MethodDoc, name: String)

    fun appendDesc(exportContext: ExportContext,
                   methodDoc: MethodDoc, desc: String?)

    fun addParam(exportContext: ExportContext,
                 methodDoc: MethodDoc, param: Param)

    fun setRet(exportContext: ExportContext,
               methodDoc: MethodDoc, ret: Any?)

    fun appendRetDesc(exportContext: ExportContext,
                      methodDoc: MethodDoc, retDesc: String?)

    fun startProcessMethod(methodExportContext: MethodExportContext, methodDoc: MethodDoc)

    fun processCompleted(methodExportContext: MethodExportContext, methodDoc: MethodDoc)
}

//region utils------------------------------------------------------------------
fun MethodDocBuilderListener.addParam(exportContext: ExportContext,
                                      methodDoc: MethodDoc, paramName: String, value: Any?, desc: String?, required: Boolean) {
    addParam(exportContext, methodDoc, paramName, value, required, desc)
}

fun MethodDocBuilderListener.addParam(exportContext: ExportContext,
                                      methodDoc: MethodDoc, paramName: String, value: Any?, required: Boolean, desc: String?) {
    val param = Param()
    param.name = paramName
    param.value = value
    param.required = required
    param.desc = desc
    this.addParam(exportContext, methodDoc, param)
}

//endregion utils------------------------------------------------------------------