package com.itangcent.test

import org.apache.commons.lang3.time.FastDateFormat
import java.util.*

object TimeZoneKit {
    const val STANDARD_TIME = 1618124194123L

    val STANDARD_TIME_GMT_STRING = FastDateFormat.getInstance(
        "EEE, dd MMM yyyyHH:mm:ss 'GMT'",
        TimeZone.getTimeZone("GMT+0:00")
    ).format(STANDARD_TIME)!!

    val LOCAL_TIME_GMT_STRING = FastDateFormat.getInstance("EEE, dd MMM yyyyHH:mm:ss 'GMT'")
        .format(STANDARD_TIME)!!

    val STANDARD_TIME_YMS_STRING = FastDateFormat.getInstance(
        "yyyy-MM-dd HH:mm:ss",
        TimeZone.getTimeZone("GMT+0:00")
    ).format(STANDARD_TIME)!!

    val LOCAL_TIME_YMS_STRING = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss")
        .format(STANDARD_TIME)!!

    val STANDARD_TIME_RAW_STRING = FastDateFormat.getInstance(
        "yyyyMMddHHmmss",
        TimeZone.getTimeZone("GMT+0:00")
    ).format(STANDARD_TIME)!!

    val LOCAL_TIME_RAW_STRING = FastDateFormat.getInstance("yyyyMMddHHmmss")
        .format(STANDARD_TIME)!!

    private val IS_STANDARD_TIME_ZONE = STANDARD_TIME_GMT_STRING == LOCAL_TIME_GMT_STRING

    fun String.fixTimeZone(): String {
        if (IS_STANDARD_TIME_ZONE) {
            return this
        }
        return this.replace(STANDARD_TIME_GMT_STRING, LOCAL_TIME_GMT_STRING)
            .replace(STANDARD_TIME_YMS_STRING, LOCAL_TIME_YMS_STRING)
            .replace(STANDARD_TIME_RAW_STRING, LOCAL_TIME_RAW_STRING)
    }
}