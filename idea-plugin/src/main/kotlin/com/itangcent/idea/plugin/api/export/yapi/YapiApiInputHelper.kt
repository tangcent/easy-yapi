package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.ImplementedBy
import java.awt.Component


@ImplementedBy(DefaultYapiApiInputHelper::class)
interface YapiApiInputHelper {

    /**
     * input yapi server url
     *
     * @return return the input yapi server or null
     */
    fun inputServer(): String?

    /**
     * input yapi server url
     *
     * @param parent the parent Component to show the input dialog
     *
     * @return return the input yapi server or null
     */
    fun inputServer(parent: Component): String?

    /**
     * input yapi server url
     *
     * @param next the next action which be called with the server url
     */
    fun inputServer(next: (String?) -> Unit)

    /**
     * input yapi server url
     *
     * @param parent the parent Component to show the input dialog
     * @param next the next action which be called with the server url
     */
    fun inputServer(parent: Component, next: (String?) -> Unit)

    /**
     * input token of the special module
     *
     * @return return the input token or null
     */
    fun inputToken(): String?

    /**
     * input token of the special module
     *
     * @param parent the parent Component to show the input dialog
     * @return return the input token or null
     */
    fun inputToken(parent: Component): String?

    /**
     * input token of the special module
     *
     * @return return the input token or null
     */
    fun inputToken(module: String): String?

    /**
     * input token of the special module
     *
     * @param parent the parent Component to show the input dialog
     * @return return the input token or null
     */
    fun inputToken(parent: Component, module: String): String?

}