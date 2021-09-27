package com.itangcent.idea.plugin.api.export.core

import com.itangcent.intellij.config.rule.*

object ClassExportRuleKeys {

    val MODULE: RuleKey<String> = SimpleRuleKey(
            "module", 
            StringRuleMode.SINGLE
    )

    val IGNORE: RuleKey<Boolean> = SimpleRuleKey(
            "ignore", 
            BooleanRuleMode.ANY
    )

    val IS_CTRL: RuleKey<Boolean> = SimpleRuleKey(
            "class.is.ctrl", 
            BooleanRuleMode.ANY
    )

    //filter class for methodDoc(rpc)
    val CLASS_FILTER: RuleKey<Boolean> = SimpleRuleKey(
            "mdoc.class.filter", 
            BooleanRuleMode.ANY
    )

    //filter method for methodDoc(rpc)
    val METHOD_FILTER: RuleKey<Boolean> = SimpleRuleKey(
            "mdoc.method.filter", 
            BooleanRuleMode.ANY
    )

    val METHOD_DOC_PATH: RuleKey<String> = SimpleRuleKey("mdoc.method.path", 
            StringRuleMode.SINGLE)

    val METHOD_DOC_METHOD: RuleKey<String> = SimpleRuleKey("mdoc.method.http.method", 
            StringRuleMode.SINGLE)

    val PARAM_DOC: RuleKey<String> = SimpleRuleKey(
            "param.doc",
            arrayOf("doc.param"),
            
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_DOC: RuleKey<String> = SimpleRuleKey(
            "method.doc",
            arrayOf("doc.method"),
            
            StringRuleMode.MERGE_DISTINCT
    )

    val CLASS_DOC: RuleKey<String> = SimpleRuleKey(
            "class.doc",
            arrayOf("doc.class"),
            
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_HEADER: RuleKey<String> = SimpleRuleKey(
            "method.additional.header", 
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_PARAM: RuleKey<String> = SimpleRuleKey(
            "method.additional.param", 
            StringRuleMode.MERGE_DISTINCT
    )

    val METHOD_ADDITIONAL_RESPONSE_HEADER: RuleKey<String> = SimpleRuleKey(
            "method.additional.response.header", 
            StringRuleMode.MERGE_DISTINCT
    )

    val PARAM_REQUIRED: RuleKey<Boolean> = SimpleRuleKey(
            "param.required", 
            BooleanRuleMode.ANY
    )

    val PARAM_IGNORE: RuleKey<Boolean> = SimpleRuleKey(
            "param.ignore", 
            BooleanRuleMode.ANY
    )

    val PARAM_DEFAULT_VALUE: RuleKey<String> = SimpleRuleKey(
            "param.default.value", 
            StringRuleMode.MERGE_DISTINCT
    )

    val PARAM_BEFORE: RuleKey<String> = SimpleRuleKey(
            "param.before", EventRule::class,
            EventRuleMode.IGNORE_ERROR
    )

    val PARAM_AFTER: RuleKey<String> = SimpleRuleKey(
            "param.after", EventRule::class,
            EventRuleMode.IGNORE_ERROR
    )

    val FIELD_REQUIRED: RuleKey<Boolean> = SimpleRuleKey(
            "field.required", 
            BooleanRuleMode.ANY
    )

    val CLASS_PREFIX_PATH: RuleKey<String> = SimpleRuleKey(
            "class.prefix.path", 
            StringRuleMode.SINGLE
    )

    /**
     * the main goal of the {@return}
     */
    val METHOD_RETURN_MAIN: RuleKey<String> = SimpleRuleKey(
            "method.return.main", 
            StringRuleMode.SINGLE
    )

    /**
     * the real return type of method
     */
    val METHOD_RETURN: RuleKey<String> = SimpleRuleKey(
            "method.return", 
            StringRuleMode.SINGLE
    )

    /**
     * The content-type of the api.
     */
    val METHOD_CONTENT_TYPE: RuleKey<String> = SimpleRuleKey(
            "method.content.type", 
            StringRuleMode.SINGLE
    )

    /**
     * The type of the param in http request.
     * Param with annotation like @RequestBody/@ModelAttribute/... do not compute this rule.
     * should return body/form/query
     */
    val PARAM_HTTP_TYPE: RuleKey<String> = SimpleRuleKey(
            "param.http.type", 
            StringRuleMode.SINGLE
    )

    /**
     * name of api
     */
    val API_NAME: RuleKey<String> = SimpleRuleKey(
            "api.name", 
            StringRuleMode.SINGLE
    )

    /**
     * folder of api
     */
    val API_FOLDER: RuleKey<String> = SimpleRuleKey(
            "folder.name", 
            StringRuleMode.SINGLE
    )

    //default http method of api(method)
    val METHOD_DEFAULT_HTTP_METHOD: RuleKey<String> = SimpleRuleKey(
            "method.default.http.method", 
            StringRuleMode.SINGLE
    )

    val FIELD_MOCK: RuleKey<String> = SimpleRuleKey(
            "field.mock", 
            StringRuleMode.SINGLE
    )

    val FIELD_DEFAULT_VALUE: RuleKey<String> = SimpleRuleKey(
            "field.default.value", 
            StringRuleMode.SINGLE
    )

    val POST_MAN_HOST: RuleKey<String> = SimpleRuleKey(
            "postman.host", 
            StringRuleMode.SINGLE
    )

    val HTTP_CLIENT_BEFORE_CALL: RuleKey<Boolean> = SimpleRuleKey(
            "http.call.before", EventRule::class,
            EventRuleMode.IGNORE_ERROR
    )

    val HTTP_CLIENT_AFTER_CALL: RuleKey<Boolean> = SimpleRuleKey(
            "http.call.after", EventRule::class,
            EventRuleMode.IGNORE_ERROR
    )

    val PATH_MULTI: RuleKey<String> = SimpleRuleKey(
            "path.multi", 
            StringRuleMode.SINGLE
    )

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
            "class.postman.prerequest", 
            StringRuleMode.MERGE
    )

    /**
     * Add pre-request scripts to top collection.
     * This script will execute before every request in this collection.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/pre-request-scripts]
     */
    val COLLECTION_POST_PRE_REQUEST: RuleKey<String> = SimpleRuleKey(
            "collection.postman.prerequest", 
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
            "class.postman.test", 
            StringRuleMode.MERGE
    )

    /**
     * Add test scripts to top collection.
     * These tests will execute after every request in this collection.
     *
     * @see [https://learning.postman.com/docs/writing-scripts/test-scripts/]
     */
    val COLLECTION_POST_TEST: RuleKey<String> = SimpleRuleKey(
            "collection.postman.test", 
            StringRuleMode.MERGE
    )

    val AFTER_EXPORT: RuleKey<String> = SimpleRuleKey(
            "export.after", EventRule::class,
            EventRuleMode.IGNORE_ERROR
    )

    val FIELD_PARSE_BEFORE: RuleKey<Boolean> = SimpleRuleKey(
            "field.parse.before", EventRule::class,
            EventRuleMode.THROW_IN_ERROR
    )

    val FIELD_PARSE_AFTER: RuleKey<Boolean> = SimpleRuleKey(
            "field.parse.after", EventRule::class,
            EventRuleMode.THROW_IN_ERROR
    )

    val CLASS_PARSE_BEFORE: RuleKey<Boolean> = SimpleRuleKey(
            "class.parse.before", EventRule::class,
            EventRuleMode.THROW_IN_ERROR
    )

    val CLASS_PARSE_AFTER: RuleKey<Boolean> = SimpleRuleKey(
            "class.parse.after", EventRule::class,
            EventRuleMode.THROW_IN_ERROR
    )
}
