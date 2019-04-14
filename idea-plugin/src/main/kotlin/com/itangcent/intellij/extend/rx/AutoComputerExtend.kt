package com.itangcent.intellij.extend.rx

import java.util.*
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.text.JTextComponent
import kotlin.reflect.KProperty0


//region 增强AutoBind0------------------------------------------------------------
// [consistent]:一对一直接绑定两个相同类型值
// [from]:直接取一个相同类型值
// [option]:转换为Optional对象求值
// [eval]:简化eval操作

fun <T> AutoComputer.AutoBind0<T>.from(param: KProperty0<T>) {
    AutoComputerUtils.from(this, param)
}

fun <T> AutoComputer.AutoBind0<T>.from(target: Any, property: String) {
    AutoComputerUtils.from(this, target, property)
}

fun AutoComputer.AutoBind0<String?>.from(param: JTextComponent) {
    AutoComputerUtils.from(this, param)
}

fun AutoComputer.AutoBind0<String?>.from(param: JLabel) {
    AutoComputerUtils.from(this, param)
}

fun AutoComputer.AutoBind0<Int?>.from(param: JList<*>) {
    AutoComputerUtils.from(this, param)
}

fun <T, P> AutoComputer.AutoBind1<T, P?>.option(exp: (Optional<P>) -> T) {
    AutoComputerUtils.option(this, exp)
}

fun <T, X : T> AutoComputer.AutoBind1<T, X>.eval() {
    AutoComputerUtils.eval(this)
}

fun <T> AutoComputer.AutoBind0<T>.consistent(param: KProperty0<T>) {
    AutoComputerUtils.consistent(this, param)
}

fun <T> AutoComputer.AutoBind0<T>.consistent(target: Any, property: String) {
    AutoComputerUtils.consistent(this, target, property)
}

fun AutoComputer.AutoBind0<String?>.consistent(param: JTextComponent) {
    AutoComputerUtils.consistent(this, param)
}

fun AutoComputer.AutoBind0<String?>.consistent(param: JLabel) {
    AutoComputerUtils.consistent(this, param)
}

fun AutoComputer.AutoBind0<Int?>.consistent(param: JList<*>) {
    AutoComputerUtils.consistent(this, param)
}
//endregion 增强AutoBind0------------------------------------------------------------
