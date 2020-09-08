package com.itangcent.idea.plugin.rule

/**
 * see @{link org.codehaus.groovy.jsr223.GroovyScriptEngineFactory}
 *
 */
class GroovyRuleParser : StandardJdkRuleParser() {
    override fun scriptType(): String {
        return "groovy"
    }

}

