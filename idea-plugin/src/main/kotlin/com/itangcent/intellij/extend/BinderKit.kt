package com.itangcent.intellij.extend

import com.itangcent.intellij.file.BeanBinder
import com.itangcent.intellij.file.CachedBeanBinder


fun <T : Any> BeanBinder<T>.lazy(): CachedBeanBinder<T> {
    return CachedBeanBinder(this)
}