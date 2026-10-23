package com.itangcent.easyapi.testFramework

import com.intellij.openapi.project.Project
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.config.ConfigReloadListener

/**
 * Makes [rules] the whole config of [project], then tells the listeners as a config reload does, so
 * caches built from the previous rules are dropped.
 */
fun updateConfig(project: Project, vararg rules: Pair<String, String>) {
    project.registerServiceInstance(
        serviceInterface = ConfigReader::class.java,
        instance = TestConfigReader.fromRules(project, *rules)
    )
    project.messageBus.syncPublisher(ConfigReloadListener.TOPIC).onConfigReloaded()
}
