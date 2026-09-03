package com.itangcent.easyapi.channel.hoppscotch

import com.itangcent.easyapi.core.rule.ContextKind
import com.itangcent.easyapi.core.rule.EventRuleMode
import com.itangcent.easyapi.core.rule.OutputShape
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.RuleKeyScheme
import com.itangcent.easyapi.core.rule.StringRuleMode
import com.itangcent.easyapi.core.rule.binding

/**
 * Hoppscotch-specific rule keys.
 *
 * These rule keys allow users to customize Hoppscotch export behavior via
 * the rule engine (e.g., in `.easy.api.yml` files):
 *
 * | Rule Key | Purpose |
 * |----------|---------|
 * | `hopp.prerequest` | Pre-request script for a method |
 * | `hopp.class.prerequest` | Pre-request script for all methods in a class |
 * | `hopp.collection.prerequest` | Pre-request script for the entire collection |
 * | `hopp.test` | Test script for a method |
 * | `hopp.class.test` | Test script for all methods in a class |
 * | `hopp.collection.test` | Test script for the entire collection |
 * | `hopp.host` | Base URL override for endpoints |
 * | `hopp.format.after` | Post-format hook |
 *
 * @see com.itangcent.easyapi.core.rule.RuleKeys for general (shared) rule keys
 */
object HoppscotchRuleKeys {
    val HOPP_PREREQUEST = RuleKey.string(
        "hopp.prerequest", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Generates a Hoppscotch pre-request script (Groovy rule; the script runs in Hoppscotch).",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING
        )
    )
    val HOPP_CLASS_PREREQUEST = RuleKey.string(
        "hopp.class.prerequest", StringRuleMode.MERGE, aliases = listOf("class.hopp.prerequest"),
        scheme = RuleKeyScheme(
            summary = "Class-level Hoppscotch pre-request script (Groovy rule).",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING
        )
    )
    val HOPP_COLLECTION_PREREQUEST = RuleKey.event(
        "hopp.collection.prerequest", aliases = listOf("collection.hopp.prerequest"),
        scheme = RuleKeyScheme(
            summary = "Collection-level pre-request event; exposes exported endpoints.",
            contextKinds = listOf(ContextKind.EMPTY),
            additionalBindings = listOf(binding("collection")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )
    val HOPP_TEST = RuleKey.string(
        "hopp.test", StringRuleMode.MERGE,
        scheme = RuleKeyScheme(
            summary = "Generates a Hoppscotch test script (Groovy rule; the script runs in Hoppscotch).",
            contextKinds = listOf(ContextKind.METHOD, ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING
        )
    )
    val HOPP_CLASS_TEST = RuleKey.string(
        "hopp.class.test", StringRuleMode.MERGE, aliases = listOf("class.hopp.test"),
        scheme = RuleKeyScheme(
            summary = "Class-level Hoppscotch test script (Groovy rule).",
            contextKinds = listOf(ContextKind.CLASS),
            outputShape = OutputShape.MERGED_STRING
        )
    )
    val HOPP_COLLECTION_TEST = RuleKey.event(
        "hopp.collection.test", aliases = listOf("collection.hopp.test"),
        scheme = RuleKeyScheme(
            summary = "Collection-level test event; exposes exported endpoints.",
            contextKinds = listOf(ContextKind.EMPTY),
            additionalBindings = listOf(binding("collection")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )
    val HOPP_HOST = RuleKey.string(
        "hopp.host",
        scheme = RuleKeyScheme(
            summary = "Base URL override for Hoppscotch endpoints.",
            contextKinds = listOf(ContextKind.CLASS, ContextKind.EMPTY),
            outputShape = OutputShape.STRING
        )
    )
    val HOPP_FORMAT_AFTER = RuleKey.event(
        "hopp.format.after", EventRuleMode.THROW_IN_ERROR,
        scheme = RuleKeyScheme(
            summary = "Runs after one Hoppscotch endpoint is formatted.",
            contextKinds = listOf(ContextKind.METHOD),
            additionalBindings = listOf(binding("endpoint")),
            outputShape = OutputShape.EVENT,
            dryRunnable = false
        )
    )
}
