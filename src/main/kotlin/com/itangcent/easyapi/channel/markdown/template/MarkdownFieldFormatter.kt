package com.itangcent.easyapi.channel.markdown.template

import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.type.JsonType

/**
 * The two field-rendering primitives shared by [TemplateModelBuilder] (which pre-computes the
 * model the default template iterates) and [TemplateHelpers] (which exposes `{{typeOf}}` /
 * `{{fieldDesc}}` to *user* templates).
 *
 * Both used to carry a byte-identical private copy of each function. One copy matters beyond
 * tidiness: the default template must render a field exactly the way the model builder does, or
 * the `{{typeOf}}` helper would disagree with the model's `type` column. The output shapes are
 * pinned byte-for-byte by
 * [com.itangcent.easyapi.channel.markdown.MarkdownTemplateParityTest]'s committed golden.
 *
 * (The removed copies claimed to "mirror `DefaultMarkdownFormatter`". That class no longer owns
 * either function — it now just delegates to [MarkdownTemplateRenderer] — so the claim was stale.)
 *
 * Pure — no PSI/VFS access; callable on any thread.
 */
internal object MarkdownFieldFormatter {

    /**
     * The display type of a field: `Single`→its display type, `Array`→`<item>[]` (recursively),
     * `Object`→`"object"`, `Map`→`"map"`.
     *
     * `Single` goes through [JsonType.toDisplayType] rather than being printed verbatim: the IR
     * vocabulary carries values JSON has no name for (`date`, `datetime`) that would otherwise
     * land in a document's type column as something neither a JSON nor a Java reader recognises.
     */
    fun formatType(model: ObjectModel): String = when (model) {
        is ObjectModel.Single -> JsonType.toDisplayType(model.type)
        is ObjectModel.Array -> "${formatType(model.item)}[]"
        is ObjectModel.Object -> "object"
        is ObjectModel.MapModel -> "map"
    }

    /**
     * A field's description: the comment and the options joined with `<br>`, each option
     * rendered as `value :desc` — or just `value` when it has no description.
     */
    fun buildFieldDescription(fieldModel: FieldModel): String {
        val parts = mutableListOf<String>()
        fieldModel.comment?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
        fieldModel.options?.takeIf { it.isNotEmpty() }?.let { options ->
            val optionDesc = options.joinToString("<br>") { opt ->
                if (opt.desc.isNullOrBlank()) "${opt.value}" else "${opt.value} :${opt.desc}"
            }
            parts.add(optionDesc)
        }
        return parts.joinToString("<br>")
    }
}
