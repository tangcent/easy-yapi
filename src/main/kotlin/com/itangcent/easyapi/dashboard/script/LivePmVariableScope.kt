package com.itangcent.easyapi.dashboard.script

import com.itangcent.easyapi.dashboard.env.EnvironmentService

class LivePmVariableScope(
    variables: Map<String, String>,
    private val onSet: (name: String, value: String) -> Unit,
    private val onUnset: (name: String) -> Unit
) : PmVariableScope(variables.toMutableMap()) {

    constructor(environmentService: EnvironmentService) : this(
        variables = environmentService.resolveAllVariables(),
        onSet = { name, value -> environmentService.setVariable(name, value) },
        onUnset = { name -> environmentService.unsetVariable(name) }
    )

    override fun set(name: String, value: Any?) {
        if (value != null) {
            super.set(name, value)
            onSet(name, value.toString())
        } else {
            super.set(name, null)
            onUnset(name)
        }
    }

    override fun unset(name: String) {
        super.unset(name)
        onUnset(name)
    }
}
