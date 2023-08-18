package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.KV
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.idea.plugin.utils.Storage.Companion.DEFAULT_GROUP
import com.itangcent.intellij.file.LocalFileRepository

/**
 * Implementation of [Storage] based on local file
 * The [LocalStorage] can be accessed cross projects.
 */
@Singleton
@ScriptTypeName("localStorage")
class LocalStorage : AbstractStorage() {

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    private val dbBeanBinderFactory: DbBeanBinderFactory<KV<String, Any?>> by lazy {
        DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.local.storage.v1.1.db").path)
        { KV.create() }
    }

    override fun clear(group: String?) {
        dbBeanBinderFactory.deleteBinder(group ?: DEFAULT_GROUP)
    }

    override fun getCache(group: String): MutableMap<String, Any?> {
        return dbBeanBinderFactory.getBeanBinder(group).tryRead() ?: KV.create()
    }

    override fun onUpdate(group: String?, cache: MutableMap<String, Any?>) {
        if (cache.isEmpty()) {
            dbBeanBinderFactory.deleteBinder(group ?: DEFAULT_GROUP)
        } else {
            dbBeanBinderFactory.getBeanBinder(group ?: DEFAULT_GROUP).save(cache as KV<String, Any?>)
        }
    }
}
