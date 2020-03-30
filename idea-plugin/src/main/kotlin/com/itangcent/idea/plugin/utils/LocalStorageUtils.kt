package com.itangcent.idea.plugin.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.utils.KV
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.intellij.file.LocalFileRepository

@Singleton
@ScriptTypeName("localStorage")
class LocalStorageUtils {

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    private var dbBeanBinderFactory: DbBeanBinderFactory<KV<Any?, Any?>>? = null

    private fun getDbBeanBinderFactory(): DbBeanBinderFactory<KV<Any?, Any?>> {
        if (dbBeanBinderFactory == null) {
            synchronized(this) {
                dbBeanBinderFactory = DbBeanBinderFactory(localFileRepository!!.getOrCreateFile(".api.local.storage.v1.1.db").path)
                { KV.create<Any?, Any?>() }
            }
        }
        return this.dbBeanBinderFactory!!
    }

    fun get(name: String?): Any? {
        return get(DEFAULT_GROUP, name)
    }

    fun get(group: String?, name: String?): Any? {
        return getDbBeanBinderFactory().getBeanBinder(group ?: DEFAULT_GROUP)
                .tryRead()?.get(name)
    }

    fun set(name: String?, value: Any?) {
        set(DEFAULT_GROUP, name, value)
    }

    fun set(group: String?, name: String?, value: Any?) {
        val beanBinder = getDbBeanBinderFactory().getBeanBinder(group ?: DEFAULT_GROUP)
        val kv = beanBinder.read()
        if (value == null) {
            kv.remove(name)
        } else {
            kv[name] = value
        }
        beanBinder.save(kv)
    }

    fun remove(name: String) {
        remove(DEFAULT_GROUP, name)
    }

    fun remove(group: String?, name: String) {
        val beanBinder = getDbBeanBinderFactory().getBeanBinder(group ?: DEFAULT_GROUP)
        val kv = beanBinder.tryRead() ?: return
        kv.remove(name)
        beanBinder.save(kv)
    }

    fun keys(): Array<Any?> {
        return keys(DEFAULT_GROUP)
    }

    fun keys(group: String?): Array<Any?> {
        return getDbBeanBinderFactory().getBeanBinder(group ?: DEFAULT_GROUP)
                .tryRead()
                ?.keys
                ?.toTypedArray()
                ?: emptyArray()
    }

    fun clear() {
        clear(DEFAULT_GROUP)
    }

    fun clear(group: String?) {
        getDbBeanBinderFactory().deleteBinder(group ?: DEFAULT_GROUP)
    }

    companion object {
        const val DEFAULT_GROUP = "default_local_group"
    }
}
