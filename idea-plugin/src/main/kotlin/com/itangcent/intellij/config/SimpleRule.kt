package com.itangcent.intellij.config

import com.itangcent.intellij.config.context.PsiElementContext

typealias SimpleStringRule = (PsiElementContext) -> String?
typealias SimpleBooleanRule = (PsiElementContext) -> Boolean
