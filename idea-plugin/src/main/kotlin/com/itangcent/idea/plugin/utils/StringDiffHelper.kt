package com.itangcent.idea.plugin.utils

import com.google.inject.ImplementedBy

@ImplementedBy(SimpleStringDiffHelper::class)
interface StringDiffHelper {

    /**
     * return score from 0-100
     * 100 means completely different
     * 0 means same string.
     */
    fun diff(str1: String, str2: String): Int
}