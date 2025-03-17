package com.itangcent.idea.plugin.api.export.postman

import com.itangcent.intellij.config.rule.EventRuleMode
import com.itangcent.intellij.config.rule.RuleKey
import com.itangcent.intellij.config.rule.SimpleRuleKey
import com.itangcent.intellij.config.rule.StringRuleMode

object PostmanExportRuleKeys {

    /**
     * The pre-request scripts in Postman to execute JavaScript before a request runs.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/pre-request-scripts]
     */
    val POST_PRE_REQUEST: RuleKey<String> = SimpleRuleKey(
        "postman.prerequest",
        StringRuleMode.MERGE
    )

    /**
     * Add pre-request scripts to entire collections as well as to folders within collections.
     * This script will execute before every request in this collection or folder.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/pre-request-scripts]
     */
    val CLASS_POST_PRE_REQUEST: RuleKey<String> = SimpleRuleKey(
        "postman.class.prerequest",
        alias = arrayOf("class.postman.prerequest"),
        StringRuleMode.MERGE
    )

    /**
     * Add pre-request scripts to top collection.
     * This script will execute before every request in this collection.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/pre-request-scripts]
     */
    val COLLECTION_POST_PRE_REQUEST: RuleKey<String> = SimpleRuleKey(
        "postman.collection.prerequest",
        alias = arrayOf("collection.postman.prerequest"),
        StringRuleMode.MERGE
    )

    /**
     * The test scripts for Postman API requests in JavaScript.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/test-scripts/]
     */
    val POST_TEST: RuleKey<String> = SimpleRuleKey(
        "postman.test",
        StringRuleMode.MERGE
    )

    /**
     * Add test scripts to entire collections as well as to folders within collections.
     * These tests will execute after every request in this collection.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/test-scripts/]
     */
    val CLASS_POST_TEST: RuleKey<String> = SimpleRuleKey(
        "postman.class.test",
        alias = arrayOf("class.postman.test"),
        StringRuleMode.MERGE
    )

    /**
     * Add test scripts to top collection.
     * These tests will execute after every request in this collection.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/test-scripts/]
     */
    val COLLECTION_POST_TEST: RuleKey<String> = SimpleRuleKey(
        "postman.collection.test",
        alias = arrayOf("collection.postman.test"),
        StringRuleMode.MERGE
    )

    val AFTER_FORMAT: RuleKey<String> = SimpleRuleKey(
        "postman.format.after",
        EventRuleMode.THROW_IN_ERROR
    )
}