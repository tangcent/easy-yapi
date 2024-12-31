package com.itangcent.idea.swing

import com.intellij.openapi.ui.Messages
import com.itangcent.idea.plugin.dialog.ConfirmationDialogLabels
import com.itangcent.mock.BaseContextTest
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.assertEquals

/**
 * Test case of [MessagesHelper]
 */
internal class MessagesHelperTest : BaseContextTest() {

    @Test
    fun showChooseWithTipDialog() {
        val messagesHelper = mock<MessagesHelper> {
            this.on(it.showChooseWithTipDialog<Int>(any(), any(), any(), any(), com.itangcent.mock.any {}))
                .then {
                    val callBack: ((Int?) -> Unit) = it.getArgument(4)!!
                    callBack(Messages.YES)
                }
        }
        assertEquals(
            Messages.YES,
            messagesHelper.showChooseWithTipDialog("msg", listOf(Messages.YES, Messages.NO), { "$it" }, { "$it" })
        )
    }

    @Test
    fun showAskWithApplyAllDialogWithApplyAll() {
        var applyAll = false
        val messagesHelper = Mockito.mock(MessagesHelper::class.java)
        Mockito.doAnswer {
            val callBack: (Int, Boolean) -> Unit = it.getArgument(2)!!
            callBack(Messages.YES, applyAll)
        }.`when`(messagesHelper)
            .showAskWithApplyAllDialog(
                Mockito.any(),
                com.itangcent.mock.any(ConfirmationDialogLabels()),
                com.itangcent.mock.any { _, _ -> })
        var ret: Int? = null
        messagesHelper.showAskWithApplyAllDialog(
            "msg",
            ConfirmationDialogLabels(),
            "test-showAskWithApplyAllDialogWithApplyAll"
        )
        {
            ret = it
        }
        assertEquals(
            Messages.YES,
            ret,
        )
        verify(messagesHelper, times(1)).showAskWithApplyAllDialog(
            Mockito.any(),
            com.itangcent.mock.any(ConfirmationDialogLabels()),
            com.itangcent.mock.any { _, _ -> })
        applyAll = true
        messagesHelper.showAskWithApplyAllDialog(
            "msg",
            ConfirmationDialogLabels(),
            "test-showAskWithApplyAllDialogWithApplyAll"
        )
        {
            ret = it
        }
        assertEquals(
            Messages.YES,
            ret,
        )
        verify(messagesHelper, times(2)).showAskWithApplyAllDialog(
            Mockito.any(),
            com.itangcent.mock.any(ConfirmationDialogLabels()),
            com.itangcent.mock.any { _, _ -> })
        messagesHelper.showAskWithApplyAllDialog(
            "msg",
            ConfirmationDialogLabels(),
            "test-showAskWithApplyAllDialogWithApplyAll"
        )
        {
            ret = it
        }
        assertEquals(
            Messages.YES,
            ret,
        )
        verify(messagesHelper, times(2)).showAskWithApplyAllDialog(
            Mockito.any(),
            com.itangcent.mock.any(ConfirmationDialogLabels()),
            com.itangcent.mock.any { _, _ -> })
    }
}