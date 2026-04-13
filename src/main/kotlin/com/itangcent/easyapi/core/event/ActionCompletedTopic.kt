package com.itangcent.easyapi.core.event

import com.intellij.openapi.project.Project
import com.intellij.util.messages.Topic

interface ActionCompletedTopic {

    fun onActionCompleted()

    companion object {
        val TOPIC: Topic<ActionCompletedTopic> = Topic.create(
            "EasyAPI Action Completed",
            ActionCompletedTopic::class.java
        )

        fun Project.syncPublish(topic: Topic<ActionCompletedTopic>) {
            messageBus.syncPublisher(TOPIC).onActionCompleted()
        }
    }
}
