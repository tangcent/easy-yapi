package com.itangcent.idea.utils

import com.google.inject.ImplementedBy
import java.nio.charset.Charset

@ImplementedBy(DefaultFileSaveHelper::class)
interface FileSaveHelper {

    fun saveOrCopy(content: String?,
                   onCopy: () -> Unit,
                   onSaveSuccess: (String) -> Unit,
                   onSaveFailed: () -> Unit)

    fun saveOrCopy(content: String?,
                   charset: Charset,
                   onCopy: () -> Unit,
                   onSaveSuccess: (String) -> Unit,
                   onSaveFailed: () -> Unit)

    fun saveOrCopy(content: String?,
                   charset: Charset,
                   defaultFileName: () -> String?,
                   onCopy: () -> Unit,
                   onSaveSuccess: (String) -> Unit,
                   onSaveFailed: () -> Unit)

    /**
     * @param content provide file content with file path.
     */
    fun saveBytes(content: (String) -> ByteArray,
                  defaultFileName: () -> String?,
                  onSaveSuccess: () -> Unit,
                  onSaveFailed: () -> Unit,
                  onSaveCancel: () -> Unit)
}