package com.itangcent.idea.binder

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.idea.utils.GsonExUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.file.BeanBinder

class DbBeanBinderFactory<T : kotlin.Any> {

    private val file: String

    protected var init: (() -> T)

    private var simpleBeanDAO: SqliteDataResourceHelper.SimpleBeanDAO? = null

    private var dbBeanBinderCache: Cache<String, BeanBinder<T>> = CacheBuilder.newBuilder()
            .maximumSize(20)
            .build()

    constructor(file: String, init: () -> T) {
        this.file = file
        this.init = init
    }

    private fun getDAO(): SqliteDataResourceHelper.SimpleBeanDAO {
        if (simpleBeanDAO == null) {
            val context = ActionContext.getContext()
            val sqliteDataResourceHelper = context!!.instance(SqliteDataResourceHelper::class)
            simpleBeanDAO = sqliteDataResourceHelper.getSimpleBeanDAO(file, "DB_BEAN_BINDER")
        }
        return simpleBeanDAO!!

    }

    fun getBeanBinder(beanBindName: String): BeanBinder<T> {
        return dbBeanBinderCache.get(beanBindName) { DbBeanBinder(beanBindName) }
    }

    fun deleteBinder(beanBindName: String) {
        getDAO().delete(beanBindName.toByteArray())
    }

    inner class DbBeanBinder : com.itangcent.intellij.file.BeanBinder<T> {

        private val beanBindName: String

        constructor(beanBindName: String) {
            this.beanBindName = beanBindName
        }

        override fun read(): T {
            return getDAO().get(beanBindName.toByteArray())
                    ?.let { GsonExUtils.fromJson<T>(String(it)) }
                    ?: return init()
        }

        override fun save(t: T) {
            getDAO().set(beanBindName.toByteArray(), GsonExUtils.toJson(t).toByteArray())
        }
    }
}

