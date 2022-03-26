package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.utils.toBool
import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.util.forEachValid

internal typealias Writer = (String) -> Unit

fun Writer.nextLine() {
    this("\n")
}

fun Writer.doubleLine() {
    this("\n\n")
}

interface TableWriter {

    fun writeHeaders()

    fun addRow(columns: Collection<*>)

    fun <T> addRows(rows: List<T>?, vararg columns: (T) -> Any?)
}

@Singleton
class TableWriterBuilder {

    @Inject
    private lateinit var configReader: ConfigReader

    fun build(
        writer: Writer,
        tableId: String,
        headers: Array<String>,
    ): TableWriter {
        return TableWriterImpl(writer, tableId, headers)
    }

    private inner class TableWriterImpl(
        private val writer: Writer,
        private val tableId: String,
        headers: Array<String>,
    ) : TableWriter {

        private val headers: Array<Pair<String, String?>>
        private val ignoreColumns: Array<Int>

        init {
            val validHeaders = ArrayList<Pair<String, String?>>()
            val ignoreColumns = ArrayList<Int>()
            headers.forEachIndexed { index, header ->
                if (configReader.first("md.table.${tableId}.${header}.ignore")?.toBool(false) == true) {
                    ignoreColumns.add(index)
                    return@forEachIndexed
                }
                validHeaders.add((configReader.first("md.table.${tableId}.${header}.name") ?: header)
                        to configReader.first("md.table.${tableId}.${header}.align"))
            }
            this.headers = validHeaders.toTypedArray()
            this.ignoreColumns = ignoreColumns.toTypedArray()
        }

        override fun writeHeaders() {
            headers.forEach { writer("| ${it.first} ") }
            writer("|\n")
            headers.forEach { writer("| ${it.second ?: "------------"} ") }
            writer("|\n")
        }

        override fun addRow(columns: Collection<*>) {
            columns.forEachIndexed { index, column ->
                if (!ignoreColumns.contains(index)) {
                    writer("| ${format(column)} ")
                }
            }
            writer("|\n")
        }

        override fun <T> addRows(rows: List<T>?, vararg columns: (T) -> Any?) {
            rows?.forEach { row ->
                addRow(columns.map { it(row) })
            }
        }

        private val trueDisplay: String by lazy {
            configReader.first("md.bool.true") ?: "YES"
        }

        private val falseDisplay: String by lazy {
            configReader.first("md.bool.false") ?: "NO"
        }

        fun format(any: Any?, escape: Boolean = true): String {
            if (any == null) {
                return ""
            }

            if (any is Boolean) {
                return if (any) trueDisplay else falseDisplay
            }

            if (escape) {
                return escape(any.toPrettyString())
            } else {
                return any.toPrettyString() ?: ""
            }
        }

        private fun escape(str: String?): String {
            if (str.isNullOrBlank()) return ""
            return str.replace("\n", "<br>")
                .replace("|", "\\|")
        }
    }

}

interface ObjectWriter {

    fun writeHeader()

    fun writeObject(obj: Any?, desc: String) {
        writeObject(obj, "", desc)
    }

    fun writeObject(obj: Any?, name: String, desc: String)
}

@Singleton
class ObjectWriterBuilder() {

    @Inject
    private lateinit var tableRenderBuilder: TableWriterBuilder

    @Inject
    private lateinit var markdownSettingsHelper: MarkdownSettingsHelper

    fun build(tableId: String, writer: Writer): ObjectWriter {
        val markdownFormatType = markdownSettingsHelper.markdownFormatType()
        return if (markdownFormatType == MarkdownFormatType.ULTIMATE) {
            UltimateObjectWriter(tableId, writer)
        } else {
            SimpleObjectWriter(tableId, writer)
        }
    }

    private abstract inner class AbstractObjectWriter(val writer: Writer) : ObjectWriter {

        abstract val tableWriter: TableWriter

        override fun writeObject(obj: Any?, name: String, desc: String) {
            writeBody(obj, name, desc)
        }

        override fun writeHeader() {
            tableWriter.writeHeaders()
        }

        abstract fun writeBody(obj: Any?, name: String, desc: String)

        protected fun addBodyProperty(deep: Int, vararg columns: Any?) {
            tableWriter.addRow(columns.mapIndexed { index, any ->
                if (index == 0 && deep > 1) {
                    return@mapIndexed "&ensp;&ensp;".repeat(deep - 1) + "&#124;─" + any
                } else {
                    return@mapIndexed any
                }
            })
        }
    }

    private inner class SimpleObjectWriter(tableId: String, writer: Writer) : AbstractObjectWriter(writer) {
        override val tableWriter: TableWriter = tableRenderBuilder.build(writer, tableId,
            arrayOf("name", "type", "desc"))

        override fun writeBody(obj: Any?, name: String, desc: String) {
            writeBody(obj, name, desc, 0)
        }

        @Suppress("UNCHECKED_CAST")
        fun writeBody(obj: Any?, name: String, desc: String, deep: Int) {

            var type: String? = null
            when (obj) {
                null -> type = "object"
                is String -> type = "string"
                is Number -> type = if (obj is Int || obj is Long) {
                    "integer"
                } else {
                    "number"
                }
                is Boolean -> type = "boolean"
            }
            if (type != null) {
                addBodyProperty(deep, name, type, desc)
                return
            }

            if (obj is Array<*>) {
                addBodyProperty(deep, name, "array", desc)

                if (obj.size > 0) {
                    obj.forEach {
                        writeBody(it, "", "", deep + 1)
                    }
                } else {
                    writeBody(null, "", "", deep + 1)
                }
            } else if (obj is Collection<*>) {
                addBodyProperty(deep, name, "array", desc)
                if (obj.size > 0) {
                    obj.forEach {
                        writeBody(it, "", "", deep + 1)
                    }
                } else {
                    writeBody(null, "", "", deep + 1)
                }
            } else if (obj is Map<*, *>) {
                if (deep > 0) {
                    addBodyProperty(deep, name, "object", desc)
                }
                var comment: Map<String, Any?>? = null
                try {
                    comment = obj[Attrs.COMMENT_ATTR] as Map<String, Any?>?
                } catch (e: Throwable) {
                }
                obj.forEachValid { k, v ->
                    val propertyDesc = KVUtils.getUltimateComment(comment, k)
                    writeBody(v, k.toString(), propertyDesc, deep + 1)
                }
            } else {
                addBodyProperty(deep, name, "object", desc)
            }
        }
    }

    private inner class UltimateObjectWriter(tableId: String, writer: Writer) : AbstractObjectWriter(writer) {
        override val tableWriter: TableWriter = tableRenderBuilder.build(writer, tableId,
            arrayOf("name", "type", "required", "default", "desc"))

        override fun writeBody(obj: Any?, name: String, desc: String) {
            writeBody(obj, name, null, null, desc, 0)
        }

        @Suppress("UNCHECKED_CAST")
        fun writeBody(obj: Any?, name: String, required: Boolean?, default: String?, desc: String, deep: Int) {

            var type: String? = null
            when (obj) {
                null -> type = "object"
                is String -> type = "string"
                is Number -> type = if (obj is Int || obj is Long) {
                    "integer"
                } else {
                    "number"
                }
                is Boolean -> type = "boolean"
            }
            if (type != null) {
                addBodyProperty(deep, name, type, required, default, desc)
                return
            }

            if (obj is Array<*>) {
                addBodyProperty(deep, name, "array", required, default, desc)
                if (obj.size > 0) {
                    obj.forEach {
                        writeBody(it, "", null, null, "", deep + 1)
                    }
                } else {
                    writeBody(null, "", null, null, "", deep + 1)
                }
            } else if (obj is Collection<*>) {
                addBodyProperty(deep, name, "array", required, default, desc)
                if (obj.size > 0) {
                    obj.forEach {
                        writeBody(it, "", null, null, "", deep + 1)
                    }
                } else {
                    writeBody(null, "", null, null, "", deep + 1)
                }
            } else if (obj is Map<*, *>) {
                if (deep > 0) {
                    addBodyProperty(deep, name, "object", required, default, desc)
                }
                val comments: HashMap<String, Any?>? = obj[Attrs.COMMENT_ATTR] as? HashMap<String, Any?>?
                val requireds: HashMap<String, Any?>? = obj[Attrs.REQUIRED_ATTR] as? HashMap<String, Any?>?
                val defaults: HashMap<String, Any?>? = obj[Attrs.DEFAULT_VALUE_ATTR] as? HashMap<String, Any?>?
                obj.forEachValid { k, v ->
                    val key = k.toString()
                    val propertyDesc: String? = KVUtils.getUltimateComment(comments, k)
                    writeBody(
                        v, key,
                        requireds?.get(key) as? Boolean,
                        defaults?.get(key) as? String,
                        propertyDesc ?: "",
                        deep + 1
                    )
                }
            } else {
                addBodyProperty(deep, name, "object", required, default, desc)
            }
        }
    }
}
