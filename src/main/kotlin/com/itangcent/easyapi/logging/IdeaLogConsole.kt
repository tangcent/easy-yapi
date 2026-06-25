package com.itangcent.easyapi.logging

object IdeaLogConsole : IdeaConsole, IdeaLog {

    override fun trace(msg: String) {
        LOG.trace(msg)
    }

    override fun debug(msg: String) {
        LOG.debug(msg)
    }

    override fun info(msg: String) {
        LOG.info(msg)
    }

    override fun warn(msg: String, t: Throwable?) {
        LOG.warn(msg, t)
    }

    override fun error(msg: String, t: Throwable?) {
        // Use warn level: LOG.error is prohibited (triggers intrusive popup); warn preserves severity without popup (R-CH-03).
        LOG.warn(msg, t)
    }
}