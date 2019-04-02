package com.itangcent.intellij.extend.rx

import java.util.*
import javax.swing.JList
import javax.swing.text.JTextComponent
import kotlin.reflect.KMutableProperty0
import kotlin.reflect.KProperty0

class AutoComputerExtend

//region 增强AutoBind0------------------------------------------------------------
// [consistent]:一对一直接绑定两个相同类型值
// [from]:直接取一个相同类型值
// [option]:转换为Optional对象求值
// [eval]:简化eval操作

fun <T> AutoComputer.AutoBind0<T>.from(param: KProperty0<T>) {
    this.with(param).eval()
}

fun <T> AutoComputer.AutoBind0<T>.from(target: Any, property: String) {
    this.with<T>(target, property).eval()
}

fun AutoComputer.AutoBind0<String?>.from(param: JTextComponent) {
    this.with(param).eval()
}

fun AutoComputer.AutoBind0<Int?>.from(param: JList<*>) {
    this.withIndex(param).eval()
}

fun <T, P> AutoComputer.AutoBind1<T, P?>.option(exp: (Optional<P>) -> T) {
    this.eval { p -> exp(Optional.ofNullable(p)) }
}

fun <T, X : T> AutoComputer.AutoBind1<T, X>.eval() {
    this.eval { r -> r }
}
//endregion 增强AutoBind0------------------------------------------------------------
