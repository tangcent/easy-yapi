package com.itangcent.idea.plugin

import com.itangcent.common.concurrent.AQSCountLatch
import com.itangcent.common.concurrent.CountLatch

interface Worker {
    fun status(): WorkerStatus

    fun waitCompleted()

    fun cancel()
}

class StatusRecorder : Worker {
    override fun status(): WorkerStatus {
        return when (aqsCountLatch.count()) {
            0 -> WorkerStatus.FREE
            else -> WorkerStatus.BUSY
        }
    }

    override fun waitCompleted() {
        while (status() == WorkerStatus.BUSY) {
            aqsCountLatch.waitFor(2000)
        }
    }

    override fun cancel() {
        //nothing
    }

    private val aqsCountLatch: CountLatch = AQSCountLatch()

    fun newWork() {
        aqsCountLatch.down()
    }

    fun endWork() {
        aqsCountLatch.up()
    }
}

enum class WorkerStatus {
    BUSY {
        override fun and(status: WorkerStatus): WorkerStatus {
            return BUSY
        }
    },
    FREE {
        override fun and(status: WorkerStatus): WorkerStatus {
            return status
        }
    };

    abstract fun and(status: WorkerStatus): WorkerStatus
}