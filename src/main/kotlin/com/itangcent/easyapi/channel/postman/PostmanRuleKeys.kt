package com.itangcent.easyapi.channel.postman

import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.EventRuleMode
import com.itangcent.easyapi.core.rule.OutputShape
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyScheme
import com.itangcent.easyapi.core.rule.StringRuleMode
import com.itangcent.easyapi.core.rule.binding

/**
 * Postman-specific rule keys.
 *
 * These rule keys allow users to customize Postman export behavior via the
 * rule engine (e.g., in `.easy.api.yml` files):
 *
 * | Rule Key | Purpose |
 * |----------|---------|
 * | `postman.prerequest` | Pre-request script for a method |
 * | `postman.class.prerequest` | Pre-request script for all methods in a class |
 * | `postman.collection.prerequest` | Collection-level pre-request event |
 * | `postman.test` | Test script for a method |
 * | `postman.class.test` | Test script for all methods in a class |
 * | `postman.collection.test` | Collection-level test event |
 * | `postman.host` | Base URL override for requests |
 * | `postman.format.after` | Post-format hook |
 *
 * ## Two ways to write a Postman script value
 *
 * The generated script is Postman JavaScript. A script rule value may be
 * written **literally** (injected as-is) or as a **Groovy rule** (the Groovy
 * expression is evaluated and its result is the injected script):
 *
 * - **Literal** — the default. The whole value is injected unchanged, so a
 *   plain Postman script needs no prefix:
 *   ```
 *   postman.test=```
 *   pm.test("status is 200", function () {
 *       pm.response.to.have.status(200);
 *   });
 *   ```
 *   ```
 * - **Groovy** — prefix the value with `groovy:`. The expression runs against
 *   the current PSI element and its result becomes the script. Use it only
 *   when the script must be computed from project code (e.g. read an annotation
 *   value, build a dynamic assertion):
 *   ```
 *   postman.prerequest=groovy: "pm.variables.set('endpoint', '" + it.name() + "');"
 *   ```
 *
 * These keys live in the channel package (DAG compliance — `core.rule.RuleKeys`
 * owns the shared keys; channels own their own). Mirrors the
 * [com.itangcent.easyapi.channel.hoppscotch.HoppscotchRuleKeys] pattern;
 * [RuleKey.collectFrom] enumerates them via reflection and
 * [PostmanChannel.ruleKeys] registers them with the registry.
 *
 * @see com.itangcent.easyapi.core.rule.RuleKeys for general (shared) rule keys
 */
object PostmanRuleKeys {

    /**
     * Postman pre-request script for a method (or all methods in a class).
     *
     * Consumed by [PostmanFormatter.buildEvents] / [PostmanFormatter.buildCollectionEvents]:
     * the resulting string is the Postman JS `exec` body.
     */
    val POSTMAN_PREREQUEST = RuleKey.string(
        "postman.prerequest", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Pre-request script that runs BEFORE the request — inject headers, compute signatures, mutate pm.request.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING,
            notes = listOf(
                "Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.",
                "response is null before the request is sent."
            )
        )
    )

    /** Class-level pre-request script (see [POSTMAN_PREREQUEST]). */
    val POSTMAN_CLASS_PREREQUEST = RuleKey.string(
        "postman.class.prerequest", StringRuleMode.MERGE, aliases = listOf("class.postman.prerequest"),
        scheme = RuleKeyScheme(
            summary = "Class-level pre-request script that runs BEFORE the request — inject headers, compute signatures, mutate pm.request.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING,
            notes = listOf(
                "Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.",
                "response is null before the request is sent."
            )
        )
    )

    /** Collection-level pre-request event; exposes exported endpoints. */
    val POSTMAN_COLLECTION_PREREQUEST = RuleKey.event(
        "postman.collection.prerequest", aliases = listOf("collection.postman.prerequest"),
        scheme = RuleKeyScheme(
            summary = "Collection-level pre-request event (Groovy rule; not a dashboard pm script).",
            contextKinds = listOf(ContextKind.EMPTY),
            additionalBindings = listOf(binding("collection")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )

    /**
     * Postman test script for a method (or all methods in a class).
     *
     * Consumed by [PostmanFormatter.buildEvents] / [PostmanFormatter.buildCollectionEvents]:
     * the resulting string is the Postman JS `exec` body.
     */
    val POSTMAN_TEST = RuleKey.string(
        "postman.test", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Test script that runs AFTER the response — assert on pm.response, store tokens via pm.environment.set.",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING,
            notes = listOf(
                "Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.",
                "response is available only after the request completes."
            )
        )
    )

    /** Class-level test script (see [POSTMAN_TEST]). */
    val POSTMAN_CLASS_TEST = RuleKey.string(
        "postman.class.test", StringRuleMode.MERGE, aliases = listOf("class.postman.test"),
        scheme = RuleKeyScheme(
            summary = "Class-level test script that runs AFTER the response — assert on pm.response, store tokens via pm.environment.set.",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING,
            notes = listOf(
                "Value is injected literally unless it starts with 'groovy:'; only groovy values see the EasyAPI it/helper/session bindings.",
                "response is available only after the request completes."
            )
        )
    )

    /** Collection-level test event; exposes exported endpoints. */
    val POSTMAN_COLLECTION_TEST = RuleKey.event(
        "postman.collection.test", aliases = listOf("collection.postman.test"),
        scheme = RuleKeyScheme(
            summary = "Collection-level test event (Groovy rule; not a dashboard pm script).",
            contextKinds = listOf(ContextKind.EMPTY),
            additionalBindings = listOf(binding("collection")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )

    /** Base URL override for Postman requests. */
    val POSTMAN_HOST = RuleKey.string(
        "postman.host",
        scheme = RuleKeyScheme(
            summary = "Base URL override for Postman requests.",
            contextKinds = listOf(ContextKind.CLASS, ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )

    /** Runs after one Postman item is created. */
    val POSTMAN_FORMAT_AFTER = RuleKey.event(
        "postman.format.after", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Runs after one Postman item is created; item and endpoint are key-specific export extensions.",
            contextKinds = listOf(ContextKind.METHOD),
            additionalBindings = listOf(binding("item"), binding("endpoint")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )
}