package com.itangcent.easyapi.channel.markdown.template

import com.itangcent.easyapi.core.psi.model.FieldModel
import com.itangcent.easyapi.core.psi.model.ObjectModel

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
     * The display type of a field: `Single`→its type word, `Array`→`<item>[]` (recursively),
     * `Object`→`"object"`, `Map`→`"map"`.
     *
     * `Single` prints its type word verbatim: the pipeline already maps date-like types to
     * `string` (`IrType.fromJavaType`), so the word a document's type column shows is always a
     * wire shape a JSON reader recognises.
     *
     * `Object` stays the literal `object` on purpose. The JSON word is sufficient here: the rows
     * below a field spell its shape out, so the type column has nothing to add — and the column is
     * a *wire-type* column, not a place to name classes. [ObjectModel.ref] is therefore not
     * consulted (see the OpenAPI converter, where a name really is needed to form a `$ref`).
     */
    fun formatType(model: ObjectModel): String = when (model) {
        is ObjectModel.Single -> model.type
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
