package com.itangcent.easyapi.core.script

sealed class ScriptScope {
    abstract val key: String
    abstract fun displayLabel(): String

    data class Project(val name: String) : ScriptScope() {
        override val key: String = "project:$name"
        override fun displayLabel(): String = "Project:$name"
    }

    data class Module(val name: String) : ScriptScope() {
        override val key: String = "module:$name"
        override fun displayLabel(): String = "Module:$name"
    }

    data class NoFolder(val projectName: String) : ScriptScope() {
        override val key: String = "no-folder:$projectName"
        override fun displayLabel(): String = "No folder"
    }

    data class Class(val qualifiedName: String) : ScriptScope() {
        override val key: String = "class:$qualifiedName"
        override fun displayLabel(): String = "Class:${qualifiedName.substringAfterLast('.')}"
    }

    data class Endpoint(val endpointKey: String) : ScriptScope() {
        override val key: String = "endpoint:$endpointKey"
        override fun displayLabel(): String = "Endpoint:${endpointKey.substringAfterLast('#')}"
    }
}

data class ScriptCache(
    val preRequestScript: String? = null,
    val postResponseScript: String? = null
)
