package com.itangcent.idea.plugin.api.export.core

import com.google.inject.ImplementedBy
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import kotlin.reflect.KClass

@ImplementedBy(DefaultClassExporter::class)
interface ClassExporter {

    /**
     * @return return true if this ClassExporter can parse the cls
     */
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