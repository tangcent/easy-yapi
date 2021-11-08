package com.itangcent.idea.plugin.api.export

interface ExportChannel {
    fun channel(): String?

    companion object {
        fun of(channel: String): ExportChannel {
            return SimpleExportChannel(channel)
        }
    }
}


private class SimpleExportChannel(val channel: String) : ExportChannel {
    override fun channel(): String {
        return channel
    }
}