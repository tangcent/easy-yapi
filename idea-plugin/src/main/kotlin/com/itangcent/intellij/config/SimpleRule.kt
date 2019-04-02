package com.itangcent.intellij.config

import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiNameIdentifierOwner

typealias SimpleStringRule = (PsiNameIdentifierOwner, PsiDocCommentOwner, PsiModifierListOwner) -> String?
typealias SimpleBooleanRule = (PsiNameIdentifierOwner, PsiDocCommentOwner, PsiModifierListOwner) -> Boolean
