package com.itangcent.intellij.extend.rx

import java.util.*
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.text.JTextComponent
import kotlin.reflect.KProperty0


//region Enhance AutoBind0------------------------------------------------------------
// [mutual]: One-to-one direct binding of two identical type values
// [from]:Take a value from the same type property directly
// [option]: Automatic convert source value to Optional
// [eval]:Simplify operation [eval]

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

fun <T> AutoComputer.AutoBind0<T>.mutual(param: KProperty0<T>) {
    AutoComputerUtils.mutual(this, param)
}

fun <T> AutoComputer.AutoBind0<T>.mutual(target: Any, property: String) {
    AutoComputerUtils.mutual(this, target, property)
}

fun AutoComputer.AutoBind0<String?>.mutual(param: JTextComponent) {
    AutoComputerUtils.mutual(this, param)
}

fun AutoComputer.AutoBind0<String?>.mutual(param: JLabel) {
    AutoComputerUtils.mutual(this, param)
}

fun AutoComputer.AutoBind0<Int?>.mutual(param: JList<*>) {
    AutoComputerUtils.mutual(this, param)
}
//endregion Enhance AutoBind0------------------------------------------------------------
