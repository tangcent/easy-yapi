package com.itangcent.idea.plugin.utils

import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.rx.ThrottleHelper
import com.itangcent.intellij.file.BeanBinder


class ThrottleCachedBeanBinder<T : Any> : BeanBinder<T> {
    private var delegate: BeanBinder<T>

    private var throttle = ThrottleHelper().build(this)

    @Volatile
    var cache: T? = null

    constructor(delegate: BeanBinder<T>) {
        this.delegate = delegate
    }

    override fun tryRead(): T? {
        if (cache == null) {
            cache = delegate.tryRead()
        }
        return cache
    }

    override fun read(): T {
        if (cache == null) {
            cache = delegate.read()
        }
        return cache as T
    }

    override fun save(t: T?) {
        cache = t
        val context = ActionContext.getContext()
        if (context == null) {
            delegate.save(t)
        } else {
            if (throttle.acquire(2000)) {
                context.runAsync {
                    Thread.sleep(2100)
                    cache?.let { cache -> delegate.save(cache) }
                }
            }
        }
    }
}

fun <T : Any> BeanBinder<T>.throttle(): ThrottleCachedBeanBinder<T> {
    return ThrottleCachedBeanBinder(this)
}