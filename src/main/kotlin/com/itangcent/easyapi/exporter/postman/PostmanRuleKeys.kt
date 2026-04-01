package com.itangcent.easyapi.exporter.postman

import com.itangcent.easyapi.rule.RuleKeys

/**
 * Postman-specific rule key aliases.
 * Delegates to [RuleKeys] constants.
 *
 * @deprecated Use [RuleKeys] directly.
 */
object PostmanRuleKeys {
    val POST_PRE_REQUEST            = RuleKeys.POSTMAN_PREREQUEST
    val CLASS_POST_PRE_REQUEST      = RuleKeys.POSTMAN_CLASS_PREREQUEST
    val COLLECTION_POST_PRE_REQUEST = RuleKeys.POSTMAN_COLLECTION_PREREQUEST
    val POST_TEST                   = RuleKeys.POSTMAN_TEST
    val CLASS_POST_TEST             = RuleKeys.POSTMAN_CLASS_TEST
    val COLLECTION_POST_TEST        = RuleKeys.POSTMAN_COLLECTION_TEST
    val POST_MAN_HOST               = RuleKeys.POSTMAN_HOST
    val AFTER_FORMAT                = RuleKeys.POSTMAN_FORMAT_AFTER
}
