package com.itangcent.idea.plugin.api.export

import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import kotlin.reflect.KClass

interface ClassExporter {
    fun export(cls: Any, docHandle: DocHandle): Boolean

    /**
     * the document type which be generate
     */
    fun support(docType: KClass<*>): Boolean
}

typealias DocHandle = (Doc) -> Unit

inline fun requestOnly(crossinline requestHandle: ((Request) -> Unit)): DocHandle {
    return {
        if (it is Request) {
            requestHandle(it)
        }
    }
}

inline fun methodDocOnly(crossinline requestHandle: ((MethodDoc) -> Unit)): DocHandle {
    return {
        if (it is MethodDoc) {
            requestHandle(it)
        }
    }
}