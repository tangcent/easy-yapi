package com.itangcent.easyapi.core.export.aspect

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.settings.module.ParsingOutputSettings
import com.itangcent.easyapi.core.settings.settings

/**
 * Removes the API name from the exported description when it duplicates the
 * first non-blank line (issue #1487).
 *
 * The name and the description are both derived from the same method comment,
 * so a multi-line comment necessarily repeats its first line in the description.
 * Comparing the two **final** endpoint fields keeps this independent of where
 * the values came from (Javadoc, `api.name` / `method.doc` rules, Swagger
 * annotations).
 *
 * [order] is `100`, so the standard `export.after` rule event (`order` `0`)
 * has already run and this aspect sees the script-mutated description.
 *
 * Enablement is the setting itself: [ApiParseAspects] reads the extension point
 * on every dispatch and filters by [isEnabled], so turning the switch off takes
 * effect on the next parse without a restart.
 */
class DescriptionTitleDedupAspect(private val project: Project) : ApiParseAspect, IdeaLog {

    override val order: Int = 100

    override fun isEnabled(): Boolean = runCatching {
        project.settings<ParsingOutputSettings>().removeDuplicateTitleFromDescription
    }.getOrElse { e ->
        LOG.warn("Failed to read removeDuplicateTitleFromDescription; defaulting to enabled", e)
        true
    }

    override suspend fun afterEndpoint(ctx: EndpointParseContext) {
        ctx.endpoint.stripDuplicatedTitle()
    }
}

/**
 * Drops the first non-blank line of [ApiEndpoint.description] when it equals
 * [ApiEndpoint.name] after trimming.
 *
 * Only the compared/removed line is trimmed; every remaining line is preserved
 * verbatim so Markdown indentation and code blocks stay intact.
 *
 * Pure function: no PSI access, no suspension — mutates the endpoint in place so
 * its identity (relied on by targeted export matching) does not change.
 */
internal fun ApiEndpoint.stripDuplicatedTitle() {
    val name = name?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val desc = description?.takeIf { it.isNotBlank() } ?: return
    val lines = desc.lines()
    val first = lines.indexOfFirst { it.isNotBlank() }
    if (first < 0 || lines[first].trim() != name) return
    description = lines.drop(first + 1).dropWhile { it.isBlank() }
        .joinToString("\n")
}
