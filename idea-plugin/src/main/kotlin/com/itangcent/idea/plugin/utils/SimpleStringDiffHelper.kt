package com.itangcent.idea.plugin.utils

import com.google.common.primitives.Ints
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
        if (str1 == str2) {
            return 0
        }
        return if (str1.length * str2.length < THRESHOLD) {
            calculateStringDistance(str1.toCharArray(), str2.toCharArray())
        } else {
            calculateDiffHasty(str1, str2)
        }
    }

    private fun calculateDiffHasty(str1: String, str2: String): Int {
        val diff1 = StringUtils.indexOfDifference(str1, str2)
        if (diff1 == -1) {
            return 100
        }
        val diff2 = StringUtils.indexOfDifference(str1.reversed(), str2.reversed())
        return 100 - ((diff1 + diff2) * 100) / (str1.length + str2.length)
    }

    private fun calculateStringDistance(strA: CharArray, strB: CharArray): Int {
        val lenA = strA.size + 1
        val lenB = strB.size + 1
        val c = arrayOfNulls<IntArray>(lenA)
        for (i in 0 until lenA) c[i] = IntArray(lenB)

        // Record the distance of all begin points of each string
        for (i in 0 until lenA) c[i]!![0] = i
        for (j in 0 until lenB) c[0]!![j] = j
        c[0]!![0] = 0
        for (i in 1 until lenA) {
            for (j in 1 until lenB) {
                if (strB[j - 1] == strA[i - 1]) {
                    c[i]!![j] = c[i - 1]!![j - 1]
                } else {
                    c[i]!![j] = Ints.min(c[i]!![j - 1], c[i - 1]!![j]) + 1
                }
            }
        }
        return (c[lenA - 1]!![lenB - 1] * 100) / (strA.size + strB.size)
    }
}

private const val THRESHOLD = 1024 * 8