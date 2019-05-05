package com.itangcent.idea.binder

import com.itangcent.idea.sqlite.SqliteDataResourceHelper
import com.itangcent.idea.utils.GsonExUtils
import com.itangcent.intellij.context.ActionContext

class DbBeanBinder<T : kotlin.Any> : com.itangcent.intellij.file.BeanBinder<T> {

    private val file: String
    private val beanBindName: String

    private var init: (() -> T)

    constructor(file: String, beanBindName: String, init: (() -> T)) {
        this.file = file
        this.beanBindName = beanBindName
        this.init = init
    }

    private var simpleBeanDAO: SqliteDataResourceHelper.SimpleBeanDAO? = null

    private fun getDAO(): SqliteDataResourceHelper.SimpleBeanDAO {
        if (simpleBeanDAO == null) {
            val context = ActionContext.getContext()
            val sqliteDataResourceHelper = context!!.instance(SqliteDataResourceHelper::class)
            simpleBeanDAO = sqliteDataResourceHelper.getSimpleBeanDAO(file, "DB_BEAN_BINDER")
        }
        return simpleBeanDAO!!

    }

    override fun read(): T {
        return getDAO().get(beanBindName.toByteArray())
                ?.let { GsonExUtils.fromJson<T>(String(it)) }
                ?: init()
    }

    override fun save(t: T) {
        getDAO().set(beanBindName.toByteArray(), GsonExUtils.toJson(t).toByteArray())
    }
}