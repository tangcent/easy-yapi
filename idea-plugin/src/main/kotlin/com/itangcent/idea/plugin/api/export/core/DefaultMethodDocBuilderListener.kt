package com.itangcent.idea.plugin.api.export.core

import com.google.inject.Singleton
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Param

@Singleton
open class DefaultMethodDocBuilderListener : MethodDocBuilderListener {
    override fun setName(exportContext: ExportContext,
                         methodDoc: MethodDoc, name: String) {
        methodDoc.name = name
    }

    override fun appendDesc(exportContext: ExportContext,
                            methodDoc: MethodDoc, desc: String?) {
        if (methodDoc.desc == null) {
            methodDoc.desc = desc
        } else {
            methodDoc.desc = "${methodDoc.desc}$desc"
        }
    }

    override fun addParam(exportContext: ExportContext,
                          methodDoc: MethodDoc, param: Param) {
        if (methodDoc.params == null) {
            methodDoc.params = ArrayList()
        }
        methodDoc.params!!.add(param)
    }

    override fun setRet(exportContext: ExportContext,
                        methodDoc: MethodDoc, ret: Any?) {
        methodDoc.ret = ret
    }

    override fun appendRetDesc(exportContext: ExportContext,
                               methodDoc: MethodDoc, retDesc: String?) {
        if (methodDoc.retDesc.isNullOrBlank()) {
            methodDoc.retDesc = retDesc
        } else {
            methodDoc.retDesc = methodDoc.retDesc + "\n" + retDesc
        }
    }

    override fun startProcessMethod(methodExportContext: MethodExportContext, methodDoc: MethodDoc) {
        //NOP
    }

    override fun processCompleted(methodExportContext: MethodExportContext, methodDoc: MethodDoc) {
        //NOP
    }
}
