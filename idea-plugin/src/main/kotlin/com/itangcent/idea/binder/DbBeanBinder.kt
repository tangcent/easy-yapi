package com.itangcent.idea.binder

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.idea.sqlite.delete
import com.itangcent.idea.sqlite.get
import com.itangcent.idea.sqlite.set
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
        dao.delete(beanBindName)
    }

    inner class DbBeanBinder(private val beanBindName: String) : BeanBinder<T> {

        override fun tryRead(): T? {
            return dao.get(beanBindName)
                ?.let { JacksonUtils.fromJson<T>(it) }
        }

        override fun read(): T {
            return dao.get(beanBindName)
                ?.let { JacksonUtils.fromJson<T>(it) }
                ?: return init()
        }

        override fun save(t: T?) {
            if (t == null) {
                dao.delete(beanBindName)
            } else {
                dao.set(beanBindName, JacksonUtils.toJson(t))
            }
        }
    }
}

