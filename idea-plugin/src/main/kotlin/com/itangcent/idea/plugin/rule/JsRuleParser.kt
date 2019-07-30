package com.itangcent.idea.plugin.rule

/**
 * see @{link jdk.nashorn.api.scripting.NashornScriptEngineFactory}
 *
 */
class JsRuleParser : StandardJdkRuleParser() {
    override fun scriptType(): String {
        return "JavaScript"
    }
}

