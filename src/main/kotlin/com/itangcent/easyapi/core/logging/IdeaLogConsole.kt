package com.itangcent.easyapi.core.logging

object IdeaLogConsole : IdeaConsole, IdeaLog {

    override fun trace(msg: String) {
        LOG.info(msg)
    }

    override fun debug(msg: String) {
        LOG.info(msg)
    }

    override fun info(msg: String) {
        LOG.info(msg)
    }

    override fun warn(msg: String, t: Throwable?) {
        LOG.warn(msg, t)
    }

    override fun error(msg: String, t: Throwable?) {
        // Use warn level: LOG.error is prohibited (triggers intrusive popup); warn preserves severity without popup.
        LOG.warn(msg, t)
    }
}