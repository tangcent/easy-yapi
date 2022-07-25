package com.itangcent.idea.plugin.settings.xml

import com.itangcent.idea.plugin.settings.PostmanExportMode
import java.io.ByteArrayOutputStream
import java.util.*

interface ProjectSettingsSupport {

    var postmanWorkspace: String?

    var postmanExportMode: String?

    var postmanCollections: String?

    var postmanBuildExample: Boolean

    var yapiTokens: String?

    fun copyTo(newSetting: ProjectSettingsSupport) {
        newSetting.postmanWorkspace = this.postmanWorkspace
        newSetting.postmanExportMode = this.postmanExportMode
        newSetting.postmanCollections = this.postmanCollections
        newSetting.postmanBuildExample = this.postmanBuildExample
        this.yapiTokens?.let { newSetting.yapiTokens = it }
    }
}

fun ProjectSettingsSupport.postmanCollectionsAsPairs(): List<Pair<String, String>> {
    val properties = Properties()
    this.postmanCollections?.byteInputStream()
        ?.let { properties.load(it) }

    return properties.entries.map { (it.key as String) to (it.value as String) }
}

fun ProjectSettingsSupport.setPostmanCollectionsPairs(pairs: List<Pair<String, String>>) {
    val properties = Properties()
    pairs.forEach { properties[it.first] = it.second }
    this.postmanCollections = ByteArrayOutputStream()
        .also { properties.store(it, "") }
        .toString()
        .removePropertiesComments()
}

fun ProjectSettingsSupport.addPostmanCollections(module: String, collectionId: String) {
    this.postmanCollectionsAsPairs()
        .toMutableList()
        .also { it.add(module to collectionId) }
        .let { this.setPostmanCollectionsPairs(it) }
}

private fun String.removePropertiesComments(): String {
    var ret = this
    while (ret.startsWith("#") && ret.contains('\n')) {
        if (ret.substringBefore('\n').contains('=')) {
            break
        }
        ret = ret.substringAfter('\n')
    }
    return ret
}

class ProjectSettings : ProjectSettingsSupport {

    override var postmanWorkspace: String? = null

    override var postmanExportMode: String? = PostmanExportMode.COPY.name

    override var postmanCollections: String? = null

    override var postmanBuildExample: Boolean = true

    override var yapiTokens: String? = null

    fun copy(): ProjectSettings {
        val projectSettings = ProjectSettings()
        this.copyTo(projectSettings)
        return projectSettings
    }
}