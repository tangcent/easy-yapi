package com.itangcent.idea.plugin.api.export

import com.google.inject.ImplementedBy

@ImplementedBy(DefaultFormatFolderHelper::class)
interface FormatFolderHelper {

    fun resolveFolder(resource: Any): Folder

}

