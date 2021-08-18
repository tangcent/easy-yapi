package com.itangcent.idea.swing

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.mock.BaseContextTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import java.awt.Component
import kotlin.test.assertEquals


/**
 * Test case of [DefaultMessagesHelper]
 */
internal open class DefaultMessagesHelperTest : BaseContextTest() {

    @Inject
    protected lateinit var messagesHelper: MessagesHelper

    @Inject
    protected lateinit var project: Project

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(MessagesHelper::class) { it.with(DefaultMessagesHelper::class) }
    }

    @Test
    fun showEditableChooseDialog() {
        actionContext.runInSwingUI {
            logger.info("test MessagesHelper.showEditableChooseDialog")
            mockStatic(Messages::class.java).use { messages ->
                messages.`when`<String> {
                    Messages.showEditableChooseDialog(
                        "Msg",
                        "Title",
                        Messages.getInformationIcon(),
                        null, null, null
                    )
                }.thenReturn("select")
                assertEquals(
                    "select", messagesHelper.showEditableChooseDialog(
                        "Msg", "Title", Messages.getInformationIcon(), null, null
                    )
                )
            }
            logger.info("test MessagesHelper.showEditableChooseDialog completed")
        }
    }

    class WithActiveWindowProviderTest : DefaultMessagesHelperTest() {

        @Inject
        private lateinit var activeWindowProvider: ActiveWindowProvider

        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)
            builder.bind(ActiveWindowProvider::class) { it.with(SimpleActiveWindowProvider::class) }
        }

        @Test
        fun showYesNoDialog() {
            actionContext.runInSwingUI {
                logger.info("test MessagesHelper.showYesNoDialog with project")
                mockStatic(Messages::class.java).use { messages ->
                    messages.`when`<Int> {
                        Messages.showYesNoDialog(
                            project,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }.thenReturn(Messages.YES)
                    assertEquals(
                        Messages.YES, messagesHelper.showYesNoDialog(
                            "Msg", "Title", Messages.getQuestionIcon()
                        )
                    )
                    messages.verify({
                        Messages.showYesNoDialog(
                            project,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }, times(1))
                }
                logger.info("test MessagesHelper.showYesNoDialog with project completed")

                logger.info("test MessagesHelper.showYesNoDialog with component")
                val component = mock<Component>()
                (activeWindowProvider as MutableActiveWindowProvider).setActiveWindow(component)
                mockStatic(Messages::class.java).use { messages ->
                    messages.`when`<Int> {
                        Messages.showYesNoDialog(
                            component,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }.thenReturn(Messages.NO)
                    assertEquals(
                        Messages.NO, messagesHelper.showYesNoDialog(
                            "Msg", "Title", Messages.getQuestionIcon()
                        )
                    )
                    messages.verify({
                        Messages.showYesNoDialog(
                            component,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }, times(1))
                }
                logger.info("test MessagesHelper.showYesNoDialog with component completed")
            }
        }

        @Test
        fun showInputDialog() {
            actionContext.runInSwingUI {
                logger.info("test MessagesHelper.showInputDialog with project")
                mockStatic(Messages::class.java).use { messages ->
                    messages.`when`<String> {
                        Messages.showInputDialog(
                            project,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }.thenReturn("yes")
                    assertEquals(
                        "yes", messagesHelper.showInputDialog(
                            "Msg", "Title", Messages.getQuestionIcon()
                        )
                    )
                }
                logger.info("test MessagesHelper.showInputDialog with project completed")

                logger.info("test MessagesHelper.showInputDialog with component")
                val component = mock<Component>()
                (activeWindowProvider as MutableActiveWindowProvider).setActiveWindow(component)
                mockStatic(Messages::class.java).use { messages ->
                    messages.`when`<String> {
                        Messages.showInputDialog(
                            component,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }.thenReturn("no")
                    assertEquals(
                        "no", messagesHelper.showInputDialog(
                            "Msg", "Title", Messages.getQuestionIcon()
                        )
                    )
                }
                logger.info("test MessagesHelper.showInputDialog with component completed")
            }
        }
    }

    class WithOutActiveWindowProviderTest : DefaultMessagesHelperTest() {

        @Test
        fun showYesNoDialog() {
            actionContext.runInSwingUI {
                logger.info("test MessagesHelper.showYesNoDialog with project")
                mockStatic(Messages::class.java).use { messages ->
                    messages.`when`<Int> {
                        Messages.showYesNoDialog(
                            project,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }.thenReturn(Messages.YES)
                    assertEquals(
                        Messages.YES, messagesHelper.showYesNoDialog(
                            "Msg", "Title", Messages.getQuestionIcon()
                        )
                    )
                    messages.verify({
                        Messages.showYesNoDialog(
                            project,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }, times(1))
                }
                logger.info("test MessagesHelper.showYesNoDialog with project completed")
            }
        }

        @Test
        fun showInputDialog() {
            actionContext.runInSwingUI {
                logger.info("test MessagesHelper.showInputDialog with project")
                mockStatic(Messages::class.java).use { messages ->
                    messages.`when`<String> {
                        Messages.showInputDialog(
                            project,
                            "Msg",
                            "Title",
                            Messages.getQuestionIcon()
                        )
                    }.thenReturn("yes")
                    assertEquals(
                        "yes", messagesHelper.showInputDialog(
                            "Msg", "Title", Messages.getQuestionIcon()
                        )
                    )
                }
                logger.info("test MessagesHelper.showInputDialog with project completed")
            }
        }
    }
}