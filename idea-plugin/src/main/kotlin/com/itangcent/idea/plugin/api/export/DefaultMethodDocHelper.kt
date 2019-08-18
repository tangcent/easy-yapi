package com.itangcent.idea.plugin.api.export

import com.google.inject.Singleton
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Param

@Singleton
class DefaultMethodDocHelper : MethodDocHelper {
    override fun setName(methodDoc: MethodDoc, name: String) {
        methodDoc.name = name
    }

    override fun appendDesc(methodDoc: MethodDoc, desc: String?) {
        methodDoc.desc = desc
    }

    override fun addParam(methodDoc: MethodDoc, param: Param) {
        if (methodDoc.params == null) {
            methodDoc.params = ArrayList()
        }
        methodDoc.params!!.add(param)
    }

    override fun setRet(methodDoc: MethodDoc, ret: Any?, retAttr: String?) {
        methodDoc.ret = ret
    }

}
