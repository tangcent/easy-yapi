package com.itangcent.idea.plugin.api.export

import com.google.inject.Singleton
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Param

@Singleton
open class DefaultMethodDocHelper : MethodDocHelper {
    override fun setName(methodDoc: MethodDoc, name: String) {
        methodDoc.name = name
    }

    override fun appendDesc(methodDoc: MethodDoc, desc: String?) {
        if (methodDoc.desc == null) {
            methodDoc.desc = desc
        } else {
            methodDoc.desc = "${methodDoc.desc}$desc"
        }
    }

    override fun addParam(methodDoc: MethodDoc, param: Param) {
        if (methodDoc.params == null) {
            methodDoc.params = ArrayList()
        }
        methodDoc.params!!.add(param)
    }

    override fun setRet(methodDoc: MethodDoc, ret: Any?) {
        methodDoc.ret = ret
    }

    override fun appendRetDesc(methodDoc: MethodDoc, retDesc: String?) {
        if (methodDoc.retDesc.isNullOrBlank()) {
            methodDoc.retDesc = retDesc
        } else {
            methodDoc.retDesc = methodDoc.retDesc + "\n" + retDesc
        }
    }

}
