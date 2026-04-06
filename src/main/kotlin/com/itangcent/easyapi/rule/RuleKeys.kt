package com.itangcent.easyapi.rule

/**
 * All rule key constants used in configuration files.
 *
 * Each key is typed — [RuleKey.StringKey] for string values, [RuleKey.BooleanKey] for booleans,
 * [RuleKey.EventKey] for side-effect events, [RuleKey.IntKey] for integers.
 *
 * Evaluate with [com.itangcent.easyapi.rule.engine.RuleEngine.evaluate]:
 * ```kotlin
 * val name: String? = engine.evaluate(RuleKeys.API_NAME, psiMethod)
 * val ignore: Boolean = engine.evaluate(RuleKeys.FIELD_IGNORE, psiField)
 * engine.evaluate(RuleKeys.JSON_CLASS_PARSE_BEFORE, psiClass)
 * ```
 */
object RuleKeys {

    // ── API metadata ──────────────────────────────────────────────
    val API_NAME            = RuleKey.string("api.name")
    val API_TAG             = RuleKey.string("api.tag")
    val API_STATUS          = RuleKey.string("api.status")
    val FOLDER_NAME         = RuleKey.string("folder.name")
    val MODULE              = RuleKey.string("module")
    val IGNORE              = RuleKey.boolean("ignore")

    // ── Method rules ──────────────────────────────────────────────
    val METHOD_DOC          = RuleKey.string("method.doc", StringRuleMode.MERGE_DISTINCT)
    val CLASS_DOC           = RuleKey.string("class.doc", StringRuleMode.MERGE_DISTINCT)
    val METHOD_DEFAULT_HTTP = RuleKey.string("method.default.http.method")
    val METHOD_CONTENT_TYPE = RuleKey.string("method.content.type")
    val METHOD_RETURN       = RuleKey.string("method.return")
    val METHOD_RETURN_MAIN  = RuleKey.string("method.return.main")
    val CLASS_PREFIX_PATH   = RuleKey.string("class.prefix.path")
    val ENDPOINT_PREFIX_PATH= RuleKey.string("endpoint.prefix.path")
    val PATH_MULTI          = RuleKey.string("path.multi")

    // ── Parameter rules ───────────────────────────────────────────
    val PARAM_NAME          = RuleKey.string("param.name")
    val PARAM_TYPE          = RuleKey.string("param.type")
    val PARAM_REQUIRED      = RuleKey.boolean("param.required")
    val PARAM_IGNORE        = RuleKey.boolean("param.ignore")
    val PARAM_DEFAULT_VALUE = RuleKey.string("param.default.value")
    val PARAM_DOC           = RuleKey.string("param.doc", StringRuleMode.MERGE_DISTINCT, aliases = listOf("doc.param"))
    val PARAM_HTTP_TYPE     = RuleKey.string("param.http.type")
    val PARAM_DEMO          = RuleKey.string("param.demo")
    val PARAM_MOCK          = RuleKey.string("param.mock")

    // ── Field rules ───────────────────────────────────────────────
    val FIELD_NAME          = RuleKey.string("field.name")
    val FIELD_NAME_PREFIX   = RuleKey.string("field.name.prefix")
    val FIELD_NAME_SUFFIX   = RuleKey.string("field.name.suffix")
    val FIELD_REQUIRED      = RuleKey.boolean("field.required")
    val FIELD_IGNORE        = RuleKey.boolean("field.ignore")
    val FIELD_DEFAULT_VALUE = RuleKey.string("field.default.value")
    val FIELD_DOC           = RuleKey.string("field.doc", StringRuleMode.MERGE, aliases = listOf("doc.field"))
    val FIELD_DEMO          = RuleKey.string("field.demo")
    val FIELD_ORDER         = RuleKey.string("field.order")
    val FIELD_ORDER_WITH    = RuleKey.string("field.order.with")
    val FIELD_MOCK          = RuleKey.string("field.mock")
    val FIELD_ADVANCED      = RuleKey.string("field.advanced", StringRuleMode.MERGE)
    val FIELD_MAX_DEPTH     = RuleKey.int("field.max.depth")
    val PARAM_MAX_DEPTH     = RuleKey.int("param.max.depth")

    // ── JSON rules ────────────────────────────────────────────────
    val JSON_FIELD_PARSE_BEFORE = RuleKey.event("json.field.parse.before")
    val JSON_FIELD_PARSE_AFTER  = RuleKey.event("json.field.parse.after")
    val JSON_CLASS_PARSE_BEFORE = RuleKey.event("json.class.parse.before")
    val JSON_CLASS_PARSE_AFTER  = RuleKey.event("json.class.parse.after")
    val JSON_ADDITIONAL_FIELD   = RuleKey.string("json.additional.field", StringRuleMode.MERGE)
    val JSON_RULE_CONVERT       = RuleKey.string("json.rule.convert")
    val JSON_UNWRAPPED          = RuleKey.boolean("json.unwrapped")
    val JSON_GROUP              = RuleKey.string("json.group")

    // ── API lifecycle events ──────────────────────────────────────
    val API_CLASS_PARSE_BEFORE  = RuleKey.event("api.class.parse.before")
    val API_CLASS_PARSE_AFTER   = RuleKey.event("api.class.parse.after")
    val API_METHOD_PARSE_BEFORE = RuleKey.event("api.method.parse.before")
    val API_METHOD_PARSE_AFTER  = RuleKey.event("api.method.parse.after")
    val API_PARAM_PARSE_BEFORE  = RuleKey.event("api.param.parse.before")
    val API_PARAM_PARSE_AFTER   = RuleKey.event("api.param.parse.after")
    val EXPORT_AFTER            = RuleKey.event("export.after")

    // ── Additional headers/params ─────────────────────────────────
    val METHOD_ADDITIONAL_HEADER          = RuleKey.string("method.additional.header", StringRuleMode.MERGE)
    val METHOD_ADDITIONAL_PARAM           = RuleKey.string("method.additional.param", StringRuleMode.MERGE)
    val METHOD_ADDITIONAL_RESPONSE_HEADER = RuleKey.string("method.additional.response.header", StringRuleMode.MERGE)

    // ── HTTP call events ──────────────────────────────────────────
    val HTTP_CALL_BEFORE = RuleKey.event("http.call.before")
    val HTTP_CALL_AFTER  = RuleKey.event("http.call.after")

    // ── Class recognizer rules ────────────────────────────────────
    val CLASS_IS_CTRL       = RuleKey.boolean("class.is.spring.ctrl", aliases = listOf("class.is.ctrl"))
    val CLASS_IS_FEIGN_CTRL = RuleKey.boolean("class.is.feign.ctrl")
    val CLASS_IS_JAXRS_CTRL = RuleKey.boolean("class.is.jaxrs.ctrl")
    val CLASS_IS_QUARKUS_CTRL = RuleKey.boolean("class.is.quarkus.ctrl")
    val CLASS_IS_GRPC         = RuleKey.boolean("class.is.grpc")

    // ── Postman rules ─────────────────────────────────────────────
    val POSTMAN_PREREQUEST            = RuleKey.string("postman.prerequest", StringRuleMode.MERGE)
    val POSTMAN_CLASS_PREREQUEST      = RuleKey.string("postman.class.prerequest", StringRuleMode.MERGE, aliases = listOf("class.postman.prerequest"))
    val POSTMAN_COLLECTION_PREREQUEST = RuleKey.event("postman.collection.prerequest", aliases = listOf("collection.postman.prerequest"))
    val POSTMAN_TEST                  = RuleKey.string("postman.test", StringRuleMode.MERGE)
    val POSTMAN_CLASS_TEST            = RuleKey.string("postman.class.test", StringRuleMode.MERGE, aliases = listOf("class.postman.test"))
    val POSTMAN_COLLECTION_TEST       = RuleKey.event("postman.collection.test", aliases = listOf("collection.postman.test"))
    val POSTMAN_HOST                  = RuleKey.string("postman.host")
    val POSTMAN_FORMAT_AFTER          = RuleKey.event("postman.format.after", EventRuleMode.THROW_IN_ERROR)

    // ── YAPI rules ────────────────────────────────────────────────
    val YAPI_EXPORT_BEFORE = RuleKey.event("yapi.export.before", EventRuleMode.THROW_IN_ERROR)
    val YAPI_SAVE_BEFORE   = RuleKey.event("yapi.save.before", EventRuleMode.THROW_IN_ERROR)
    val YAPI_SAVE_AFTER    = RuleKey.event("yapi.save.after", EventRuleMode.THROW_IN_ERROR)

    // ── Enum rules ────────────────────────────────────────────────
    val ENUM_USE_CUSTOM = RuleKey.string("enum.use.custom")
    val CONSTANT_FIELD_IGNORE = RuleKey.boolean("constant.field.ignore")
}
