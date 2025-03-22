package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.ProvidedBy
import com.itangcent.common.model.Doc

/**
 * format [com.itangcent.common.model.Doc] to `markdown`.
 *
 * @author tangcent
 */
@ProvidedBy(MarkdownFormatterProvider::class)
interface MarkdownFormatter {

    fun parseDocs(docs: List<Doc>): String

}
