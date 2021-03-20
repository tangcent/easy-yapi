package com.itangcent.idea.plugin

import com.itangcent.common.utils.ThreadPoolUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertTimeout
import java.time.Duration

internal class StatusRecorderTest {

    @Test
    fun status() {
        val statusRecorder = StatusRecorder()
        val executorService = ThreadPoolUtils.createPool(5, "work")
        for (i in 0..10) {
            statusRecorder.newWork()
            executorService.submit {
                try {
                    Thread.sleep(100)
                } finally {
                    statusRecorder.endWork()
                }
            }
        }
        assertTimeout(Duration.ofSeconds(2)) {
            statusRecorder.waitCompleted()
        }
    }
}