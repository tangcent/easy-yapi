package com.itangcent.idea.plugin.api.export

import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import kotlin.reflect.KClass

interface ClassExporter {

    /**
     * @return return true if this ClassExporter can parse the cls
     */
    fun export(cls: Any, docHandle: DocHandle): Boolean {
        return export(cls, docHandle, EMPTY_COMPLETED_HANDLE)
    }

    /**
     * @return return true if any api be found
     */
    fun export(cls: Any, docHandle: DocHandle, completedHandle: CompletedHandle): Boolean

    /**
     * the document type which be generate
     */
    fun support(docType: KClass<*>): Boolean
}

typealias CompletedHandle = (Any) -> Unit

private val EMPTY_COMPLETED_HANDLE: CompletedHandle = {}

typealias DocHandle = (Doc) -> Unit

inline fun requestOnly(crossinline requestHandle: ((Request) -> Unit)): DocHandle {
    return {
        if (it is Request) {
            requestHandle(it)
        }
    }
}

inline fun methodDocOnly(crossinline methodDocHandle: ((MethodDoc) -> Unit)): DocHandle {
    return {
        if (it is MethodDoc) {
            methodDocHandle(it)
        }
    }
}

inline fun docs(crossinline docHandle: DocHandle): DocHandle {
    return {
        docHandle(it)
    }
}