package com.itangcent.mock

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.logger.Logger
import java.nio.charset.Charset

@Singleton
class FileSaveHelperAdaptor : FileSaveHelper {

    @Inject
    private lateinit var logger: Logger

    private var content: String? = null
    private var bytes: ByteArray? = null

    override fun saveOrCopy(content: String?, onCopy: () -> Unit, onSaveSuccess: (String) -> Unit, onSaveFailed: (String?) -> Unit) {
        log(content)
    }

    override fun saveOrCopy(content: String?, charset: Charset, onCopy: () -> Unit, onSaveSuccess: (String) -> Unit, onSaveFailed: (String?) -> Unit) {
        log(content)
    }

    override fun saveOrCopy(content: String?, charset: Charset, defaultFileName: () -> String?, onCopy: () -> Unit, onSaveSuccess: (String) -> Unit, onSaveFailed: (String?) -> Unit) {
        log(content)
    }

    override fun saveBytes(content: (String) -> ByteArray, defaultFileName: () -> String?, onSaveSuccess: () -> Unit, onSaveFailed: (String?) -> Unit, onSaveCancel: () -> Unit) {
        this.bytes = content("/")
    }

    fun content(): String? {
        return this.content
    }

    fun bytes(): ByteArray? {
        return this.bytes
    }

    private fun log(content: String?) {
        this.content = content
        logger.info("[content]:$content")
    }

}