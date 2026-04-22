package com.itangcent.easyapi.settings.state

import com.itangcent.easyapi.settings.PostmanExportMode
import java.io.ByteArrayOutputStream
import java.util.Properties

/**
 * Interface defining project-level settings properties.
 * Settings that are specific to individual projects.
 */
interface ProjectSettingsSupport {
    var postmanWorkspace: String?
    var postmanExportMode: String?
    var postmanCollections: String?
    var postmanBuildExample: Boolean
    var yapiTokens: String?

    /**
     * Copies settings to another instance.
     * 
     * @param newSetting The target instance to copy to
     */
    fun copyTo(newSetting: ProjectSettingsSupport) {
        newSetting.postmanWorkspace = this.postmanWorkspace
        newSetting.postmanExportMode = this.postmanExportMode
        newSetting.postmanCollections = this.postmanCollections
        newSetting.postmanBuildExample = this.postmanBuildExample
        this.yapiTokens?.let { newSetting.yapiTokens = it }
    }
}

/**
 * Interface defining application-level settings properties.
 * Settings that apply globally across all projects.
 */
interface ApplicationSettingsSupport {
    var feignEnable: Boolean
    var jaxrsEnable: Boolean
    var actuatorEnable: Boolean
    var grpcEnable: Boolean
    var extensionConfigs: String
    var postmanToken: String?
    var wrapCollection: Boolean
    var autoMergeScript: Boolean
    var postmanJson5FormatType: String
    var queryExpanded: Boolean
    var formExpanded: Boolean
    var pathMulti: String
    /** When true, auto-detect the generic field in response wrapper types as the return main field */
    var inferReturnMain: Boolean
    var yapiServer: String?
    var yapiTokens: String?
    var enableUrlTemplating: Boolean
    var switchNotice: Boolean
    var yapiExportMode: String
    var yapiReqBodyJson5: Boolean
    var yapiResBodyJson5: Boolean
    var httpTimeOut: Int
    var unsafeSsl: Boolean
    var httpClient: String
    var logLevel: Int
    var outputDemo: Boolean
    var outputCharset: String
    var markdownFormatType: String
    var builtInConfig: String?
    var remoteConfig: Array<String>
    /** When true, automatically scan APIs on file changes */
    var autoScanEnabled: Boolean
    /** gRPC artifact configurations in format: groupId:artifactId[:version][:enabled] */
    var grpcArtifactConfigs: Array<String>
    /** Additional JAR paths for gRPC runtime */
    var grpcAdditionalJars: Array<String>
    /** Enable gRPC call functionality */
    var grpcCallEnabled: Boolean
    /** Repository paths for gRPC runtime resolution in format: type:enabled:path */
    var grpcRepositories: Array<String>
    /** Enable concurrent API scanning for better performance */
    var concurrentScanEnabled: Boolean

    /**
     * Copies settings to another instance.
     * 
     * @param newSetting The target instance to copy to
     */
    fun copyTo(newSetting: ApplicationSettingsSupport) {
        newSetting.postmanToken = this.postmanToken
        newSetting.wrapCollection = this.wrapCollection
        newSetting.autoMergeScript = this.autoMergeScript
        newSetting.postmanJson5FormatType = this.postmanJson5FormatType
        newSetting.feignEnable = this.feignEnable
        newSetting.jaxrsEnable = this.jaxrsEnable
        newSetting.actuatorEnable = this.actuatorEnable
        newSetting.grpcEnable = this.grpcEnable
        newSetting.extensionConfigs = this.extensionConfigs
        newSetting.queryExpanded = this.queryExpanded
        newSetting.formExpanded = this.formExpanded
        newSetting.pathMulti = this.pathMulti
        newSetting.inferReturnMain = this.inferReturnMain
        newSetting.yapiServer = this.yapiServer
        newSetting.yapiTokens = this.yapiTokens
        newSetting.enableUrlTemplating = this.enableUrlTemplating
        newSetting.switchNotice = this.switchNotice
        newSetting.yapiExportMode = this.yapiExportMode
        newSetting.yapiReqBodyJson5 = this.yapiReqBodyJson5
        newSetting.yapiResBodyJson5 = this.yapiResBodyJson5
        newSetting.logLevel = this.logLevel
        newSetting.outputDemo = this.outputDemo
        newSetting.outputCharset = this.outputCharset
        newSetting.markdownFormatType = this.markdownFormatType
        newSetting.builtInConfig = this.builtInConfig
        newSetting.httpTimeOut = this.httpTimeOut
        newSetting.unsafeSsl = this.unsafeSsl
        newSetting.httpClient = this.httpClient
        newSetting.remoteConfig = this.remoteConfig
        newSetting.autoScanEnabled = this.autoScanEnabled
        newSetting.grpcArtifactConfigs = this.grpcArtifactConfigs
        newSetting.grpcAdditionalJars = this.grpcAdditionalJars
        newSetting.grpcCallEnabled = this.grpcCallEnabled
        newSetting.grpcRepositories = this.grpcRepositories
        newSetting.concurrentScanEnabled = this.concurrentScanEnabled
    }
}

/**
 * Parses Postman collections from properties format.
 * @return List of (collection name, collection ID) pairs
 */
fun ProjectSettingsSupport.postmanCollectionsAsPairs(): List<Pair<String, String>> {
    val properties = Properties()
    this.postmanCollections?.byteInputStream()?.let { properties.load(it) }
    return properties.entries.map { (it.key as String) to (it.value as String) }
}

/**
 * Serializes Postman collections to properties format.
 * @param pairs List of (collection name, collection ID) pairs
 */
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

fun defaultPostmanExportMode(): String = PostmanExportMode.CREATE_NEW.name

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
