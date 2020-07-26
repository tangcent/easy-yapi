package com.itangcent.idea.utils

import com.google.inject.ImplementedBy
import java.io.File

@ImplementedBy(DefaultFileSelectHelper::class)
interface FileSelectHelper {

    fun selectFile(onSelect: (File?) -> Unit)

    fun selectFile(onSelect: (File) -> Unit,
                   onCancel: () -> Unit)
}