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
import java.awt.Component
import kotlin.test.assertEquals
import kotlin.test.assertNull


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
        assertNull(
            messagesHelper.showEditableChooseDialog<DefaultMessagesHelperTestData>(
                "Msg", "Title", Messages.getInformationIcon(), null, { it.name }, null
            )
        )

        assertNull(
            messagesHelper.showEditableChooseDialog<DefaultMessagesHelperTestData>(
                "Msg", "Title", Messages.getInformationIcon(), emptyArray(), { it.name }, null
            )
        )

        actionContext.runInSwingUI {
            logger.info("test MessagesHelper.showEditableChooseDialog")
            mockStatic(Messages::class.java).use { messages ->
                messages.`when`<String> {
                    Messages.showEditableChooseDialog(
                        "Msg",
                        "Title",
                        Messages.getInformationIcon(),
                        null, "", null
                    )
                }.thenReturn("select")
                assertEquals(
                    "select", messagesHelper.showEditableChooseDialog(
                        "Msg", "Title", Messages.getInformationIcon(), null, null
                    )
                )
            }

            mockStatic(Messages::class.java).use { messages ->
                messages.`when`<String> {
                    Messages.showEditableChooseDialog(
                        "Msg",
                        "Title",
                        Messages.getInformationIcon(),
                        arrayOf("a", "b", "c", "d"),
                        "a",
                        null
                    )
                }.thenReturn("b")

                assertEquals(
                    DefaultMessagesHelperTestData(2, "b"), messagesHelper.showEditableChooseDialog(
                        "Msg", "Title", Messages.getInformationIcon(),
                        arrayOf(
                            DefaultMessagesHelperTestData(1, "a"),
                            DefaultMessagesHelperTestData(2, "b"),
                            DefaultMessagesHelperTestData(3, "c"),
                            DefaultMessagesHelperTestData(4, "d")
                        ),
                        { it.name },
                        DefaultMessagesHelperTestData(1, "a")
                    )
                )
            }

            mockStatic(Messages::class.java).use { messages ->
                messages.`when`<String> {
                    Messages.showEditableChooseDialog(
                        "Msg",
                        "Title",
                        Messages.getInformationIcon(),
                        arrayOf("a", "b", "c", "d"),
                        "a",
                        null
                    )
                }.thenReturn("e")

                assertNull(
                    messagesHelper.showEditableChooseDialog(
                        "Msg", "Title", Messages.getInformationIcon(),
                        arrayOf(
                            DefaultMessagesHelperTestData(1, "a"),
                            DefaultMessagesHelperTestData(2, "b"),
                            DefaultMessagesHelperTestData(3, "c"),
                            DefaultMessagesHelperTestData(4, "d")
                        ),
                        { it.name },
                        DefaultMessagesHelperTestData(1, "a")
                    )
                )
            }

            mockStatic(Messages::class.java).use { messages ->

                messages.`when`<String> {
                    Messages.showEditableChooseDialog(
                        "Msg",
                        "Title",
                        Messages.getInformationIcon(),
                        arrayOf("a", "b", "c", "d"),
                        "a",
                        null
                    )
                }.thenReturn("c")

                assertEquals(
                    DefaultMessagesHelperTestData(3, "c"), messagesHelper.showEditableChooseDialog(
                        "Msg", "Title", Messages.getInformationIcon(),
                        arrayOf(
                            DefaultMessagesHelperTestData(1, "a"),
                            DefaultMessagesHelperTestData(2, "b"),
                            DefaultMessagesHelperTestData(3, "c"),
                            DefaultMessagesHelperTestData(4, "d")
                        ),
                        { it.name },
                        DefaultMessagesHelperTestData(5, "a")
                    )
                )
            }

            mockStatic(Messages::class.java).use { messages ->
                messages.`when`<String> {
                    Messages.showEditableChooseDialog(
                        "Msg",
                        "Title",
                        Messages.getInformationIcon(),
                        arrayOf("1: a", "2: b", "3: b", "4: d"),
                        "1: a",
                        null
                    )
                }.thenReturn("3: b")

                assertEquals(
                    DefaultMessagesHelperTestData(3, "b"), messagesHelper.showEditableChooseDialog(
                        "Msg", "Title", Messages.getInformationIcon(),
                        arrayOf(
                            DefaultMessagesHelperTestData(1, "a"),
                            DefaultMessagesHelperTestData(2, "b"),
                            DefaultMessagesHelperTestData(3, "b"),
                            DefaultMessagesHelperTestData(4, "d")
                        ),
                        { it.name },
                        DefaultMessagesHelperTestData(1, "a")
                    )
                )
            }

            mockStatic(Messages::class.java).use { messages ->
                messages.`when`<String> {
                    Messages.showEditableChooseDialog(
                        "Msg",
                        "Title",
                        Messages.getInformationIcon(),
                        arrayOf("1: a", "2: b", "3: b", "4: d"),
                        "",
                        null
                    )
                }.thenReturn("2: b")

                assertEquals(
                    DefaultMessagesHelperTestData(2, "b"), messagesHelper.showEditableChooseDialog(
                        "Msg", "Title", Messages.getInformationIcon(),
                        arrayOf(
                            DefaultMessagesHelperTestData(1, "a"),
                            DefaultMessagesHelperTestData(2, "b"),
                            DefaultMessagesHelperTestData(3, "b"),
                            DefaultMessagesHelperTestData(4, "d")
                        ),
                        { it.name },
                        DefaultMessagesHelperTestData(5, "d")
                    )
                )
            }

            mockStatic(Messages::class.java).use { messages ->
                messages.`when`<String> {
                    Messages.showEditableChooseDialog(
                        "Msg",
                        "Title",
                        Messages.getInformationIcon(),
                        arrayOf("1: a", "2: b", "3: b", "4: d"),
                        "",
                        null
                    )
                }.thenReturn("5: f")

                assertNull(
                    messagesHelper.showEditableChooseDialog(
                        "Msg", "Title", Messages.getInformationIcon(),
                        arrayOf(
                            DefaultMessagesHelperTestData(1, "a"),
                            DefaultMessagesHelperTestData(2, "b"),
                            DefaultMessagesHelperTestData(3, "b"),
                            DefaultMessagesHelperTestData(4, "d")
                        ),
                        { it.name },
                        DefaultMessagesHelperTestData(5, "d")
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

class DefaultMessagesHelperTestData(var id: Int, var name: String) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DefaultMessagesHelperTestData

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id
    }
}