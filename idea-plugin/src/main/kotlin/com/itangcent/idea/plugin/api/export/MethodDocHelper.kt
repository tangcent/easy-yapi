package com.itangcent.idea.plugin.api.export

import com.google.inject.ImplementedBy
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Param

@ImplementedBy(DefaultMethodDocHelper::class)
interface MethodDocHelper {

    fun setName(methodDoc: MethodDoc, name: String)

    fun appendDesc(methodDoc: MethodDoc, desc: String?)

    fun addParam(methodDoc: MethodDoc, param: Param)

    fun setRet(methodDoc: MethodDoc, ret: Any?)

    fun appendRetDesc(methodDoc: MethodDoc, retDesc: String?)
}

//region utils------------------------------------------------------------------
fun MethodDocHelper.addParam(methodDoc: MethodDoc, paramName: String, value: Any?, desc: String?,required: Boolean) {
    addParam(methodDoc, paramName, value, required, desc)
}

fun MethodDocHelper.addParam(methodDoc: MethodDoc, paramName: String, value: Any?, required: Boolean, desc: String?) {
    val param = Param()
    param.name = paramName
    param.value = value
    param.required = required
    param.desc = desc
    this.addParam(methodDoc, param)
}

//endregion utils------------------------------------------------------------------