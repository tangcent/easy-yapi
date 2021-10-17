package com.itangcent.idea.plugin.api.call

import com.google.inject.ImplementedBy
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.dialog.ApiCallDialog
import com.itangcent.idea.plugin.ui.UI

@ImplementedBy(ApiCallDialog::class)
interface ApiCallUI : UI {

    fun updateRequestList(requestList: List<Request>?)

}