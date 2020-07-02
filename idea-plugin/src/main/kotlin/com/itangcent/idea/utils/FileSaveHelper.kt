package com.itangcent.idea.utils

import com.google.inject.ImplementedBy
import java.nio.charset.Charset

@ImplementedBy(DefaultFileSaveHelper::class)
interface FileSaveHelper {

    fun saveOrCopy(content: String?,
                   onCopy: () -> Unit,
                   onSaveSuccess: (String) -> Unit,
                   onSaveFailed: (String?) -> Unit)

    fun saveOrCopy(content: String?,
                   charset: Charset,
                   onCopy: () -> Unit,
                   onSaveSuccess: (String) -> Unit,
                   onSaveFailed: (String?) -> Unit)

    fun saveOrCopy(content: String?,
                   charset: Charset,
                   defaultFileName: () -> String?,
                   onCopy: () -> Unit,
                   onSaveSuccess: (String) -> Unit,
                   onSaveFailed: (String?) -> Unit)

    /**
     * @param content provide file content with file path.
     */
    fun saveBytes(content: (String) -> ByteArray,
                  defaultFileName: () -> String?,
                  onSaveSuccess: () -> Unit,
                  onSaveFailed: (String?) -> Unit,
                  onSaveCancel: () -> Unit)
}