package com.itangcent.idea.plugin.api.export

import com.itangcent.intellij.config.rule.*

object ClassExportRuleKeys {

    val MODULE: RuleKey<String> = SimpleRuleKey(
            "module", StringRule::class,
            StringRuleMode.SINGLE
    )

    val IGNORE: RuleKey<Boolean> = SimpleRuleKey(
            "ignore", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    //filter class for methodDoc(rpc)
    val CLASS_FILTER: RuleKey<Boolean> = SimpleRuleKey(
            "mdoc.class.filter", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    //filter method for methodDoc(rpc)
    val METHOD_FILTER: RuleKey<Boolean> = SimpleRuleKey(
            "mdoc.method.filter", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_DOC: RuleKey<String> = SimpleRuleKey(
            "doc.param", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_DOC: RuleKey<String> = SimpleRuleKey(
            "doc.method", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val CLASS_DOC: RuleKey<String> = SimpleRuleKey(
            "doc.class", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_HEADER: RuleKey<String> = SimpleRuleKey(
            "method.additional.header", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_PARAM: RuleKey<String> = SimpleRuleKey(
            "method.additional.param", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_RESPONSE_HEADER: RuleKey<String> = SimpleRuleKey(
            "method.additional.response.header", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val PARAM_REQUIRED: RuleKey<Boolean> = SimpleRuleKey(
            "param.required", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_IGNORE: RuleKey<Boolean> = SimpleRuleKey(
            "param.ignore", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val PARAM_DEFAULT_VALUE: RuleKey<String> = SimpleRuleKey(
            "param.default.value", StringRule::class,
            StringRuleMode.MERGE_DISTINCT
    )

    val FIELD_REQUIRED: RuleKey<Boolean> = SimpleRuleKey(
            "field.required", BooleanRule::class,
            BooleanRuleMode.ANY
    )

    val CLASS_PREFIX_PATH: RuleKey<String> = SimpleRuleKey(
            "class.prefix.path", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * the main goal of the {@return}
     */
    val METHOD_RETURN_MAIN: RuleKey<String> = SimpleRuleKey(
            "method.return.main", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * the real return type of method
     */
    val METHOD_RETURN: RuleKey<String> = SimpleRuleKey(
            "method.return", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * The content-type of the api.
     */
    val METHOD_CONTENT_TYPE: RuleKey<String> = SimpleRuleKey(
            "method.content.type", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * The type of the param in http request.
     * Param with annotation like @RequestBody/@ModelAttribute/... do not compute this rule.
     * should return body/form/query
     */
    val PARAM_HTTP_TYPE: RuleKey<String> = SimpleRuleKey(
            "param.http.type", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * name of api
     */
    val API_NAME: RuleKey<String> = SimpleRuleKey(
            "api.name", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * folder of api
     */
    val API_FOLDER: RuleKey<String> = SimpleRuleKey(
            "folder.name", StringRule::class,
            StringRuleMode.SINGLE
    )

    //default http method of api(method)
    val METHOD_DEFAULT_HTTP_METHOD: RuleKey<String> = SimpleRuleKey(
            "method.default.http.method", StringRule::class,
            StringRuleMode.SINGLE
    )

    val POST_MAN_HOST: RuleKey<String> = SimpleRuleKey(
            "postman.host", StringRule::class,
            StringRuleMode.SINGLE
    )

    val HTTP_CLIENT_BEFORE_CALL: RuleKey<Boolean> = SimpleRuleKey(
            "http.call.before", StringRule::class,
            StringRuleMode.SINGLE
    )

    val HTTP_CLIENT_AFTER_CALL: RuleKey<Boolean> = SimpleRuleKey(
            "http.call.after", StringRule::class,
            StringRuleMode.SINGLE
    )

    val PATH_MULTI: RuleKey<String> = SimpleRuleKey(
            "path.multi", StringRule::class,
            StringRuleMode.SINGLE
    )

    /**
     * The pre-request scripts in Postman to execute JavaScript before a request runs.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/pre-request-scripts]
     */
    val POST_PRE_REQUEST: RuleKey<String> = SimpleRuleKey(
            "postman.prerequest", StringRule::class,
            StringRuleMode.MERGE
    )

    /**
     * Add pre-request scripts to entire collections as well as to folders within collections.
     * This script will execute before every request in this collection or folder.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/pre-request-scripts]
     */
    val CLASS_POST_PRE_REQUEST: RuleKey<String> = SimpleRuleKey(
            "class.postman.prerequest", StringRule::class,
            StringRuleMode.MERGE
    )

    /**
     * Add pre-request scripts to top collection.
     * This script will execute before every request in this collection.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/pre-request-scripts]
     */
    val COLLECTION_POST_PRE_REQUEST: RuleKey<String> = SimpleRuleKey(
            "collection.postman.prerequest", StringRule::class,
            StringRuleMode.MERGE
    )

    /**
     * The test scripts for Postman API requests in JavaScript.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/test-scripts/]
     */
    val POST_TEST: RuleKey<String> = SimpleRuleKey(
            "postman.test", StringRule::class,
            StringRuleMode.MERGE
    )

    /**
     * Add test scripts to entire collections as well as to folders within collections.
     * These tests will execute after every request in this collection.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/test-scripts/]
     */
    val CLASS_POST_TEST: RuleKey<String> = SimpleRuleKey(
            "class.postman.test", StringRule::class,
            StringRuleMode.MERGE
    )

    /**
     * Add test scripts to top collection.
     * These tests will execute after every request in this collection.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/test-scripts/]
     */
    val COLLECTION_POST_TEST: RuleKey<String> = SimpleRuleKey(
            "collection.postman.test", StringRule::class,
            StringRuleMode.MERGE
    )

    val AFTER_EXPORT: RuleKey<String> = SimpleRuleKey(
            "export.after", StringRule::class,
            StringRuleMode.SINGLE
    )
}
