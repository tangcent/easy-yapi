package com.itangcent.idea.plugin.utils

import org.apache.commons.lang3.StringUtils

/**
 * Temporary implement of [StringDiffHelper].
 * It will be optimized and refactor in the future.
 *
 * Only try check prefix and suffix.
 * Diff of strings like 'xaaaaaaaax' and 'yaaaaaaaay' will get a lower score than expected.
 */
class SimpleStringDiffHelper : StringDiffHelper {
    override fun diff(str1: String, str2: String): Int {
        val diff1 = StringUtils.indexOfDifference(str1, str2)
        if (diff1 == -1) {
            return 0;
        }
        val diff2 = StringUtils.indexOfDifference(str1.reversed(), str2.reversed())
        return 100 - ((diff1 + diff2) * 100) / (str1.length + str2.length)
    }
}