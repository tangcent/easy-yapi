package com.itangcent.idea.binder

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.idea.utils.JacksonUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.BeanBinder

class DbBeanBinderFactory<T : Any>(private val file: String, protected var init: () -> T) {

    private val dao: SqliteDataResourceHelper.SimpleBeanDAO by lazy {
        val context = ActionContext.getContext()
        val sqliteDataResourceHelper = context!!.instance(SqliteDataResourceHelper::class)
        sqliteDataResourceHelper.getSimpleBeanDAO(this.file, "DB_BEAN_BINDER")
    }

    private var dbBeanBinderCache: Cache<String, BeanBinder<T>> = CacheBuilder.newBuilder()
            .maximumSize(20)
            .build()

    fun getBeanBinder(beanBindName: String): BeanBinder<T> {
        return dbBeanBinderCache.get(beanBindName) { DbBeanBinder(beanBindName) }
    }

    fun deleteBinder(beanBindName: String) {
        dao.delete(beanBindName.toByteArray())
    }

    inner class DbBeanBinder : BeanBinder<T> {
        private val beanBindName: String

        constructor(beanBindName: String) {
            this.beanBindName = beanBindName
        }

        override fun tryRead(): T? {
            return dao.get(beanBindName.toByteArray())
                    ?.let { JacksonUtils.fromJson<T>(String(it)) }
        }

        override fun read(): T {
            return dao.get(beanBindName.toByteArray())
                    ?.let { JacksonUtils.fromJson<T>(String(it)) }
                    ?: return init()
        }

        override fun save(t: T?) {
            if (t == null) {
                dao.delete(beanBindName.toByteArray())
            } else {
                dao.set(beanBindName.toByteArray(), JacksonUtils.toJson(t).toByteArray())
            }
        }
    }
}

