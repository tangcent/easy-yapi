package com.itangcent.idea.utils

import com.google.inject.ImplementedBy
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.charset.Charset

@ImplementedBy(DefaultFileSelectHelper::class)
interface FileSelectHelper {

    fun selectFile(onSelect: (File?) -> Unit)

    fun selectFile(onSelect: (File) -> Unit,
                   onCancel: () -> Unit)
}