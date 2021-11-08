package com.itangcent.idea.plugin.api.export

interface ExportDoc {

    fun doc(): Array<String>?

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun of(vararg channel: String): ExportDoc {
            return SimpleExportDoc(channel as Array<String>)
        }
    }
}


private class SimpleExportDoc(val doc: Array<String>) : ExportDoc {
    override fun doc(): Array<String> {
        return doc
    }
}