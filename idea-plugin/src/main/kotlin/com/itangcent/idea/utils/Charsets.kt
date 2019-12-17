package com.itangcent.idea.utils

import java.nio.charset.Charset

abstract class Charsets {

    companion object {

        val UTF_8 = ImmutableCharset(kotlin.text.Charsets.UTF_8)
        val US_ASCII = ImmutableCharset(kotlin.text.Charsets.US_ASCII)
        val UTF_16 = ImmutableCharset(kotlin.text.Charsets.UTF_16)
        val ISO_8859_1 = ImmutableCharset(kotlin.text.Charsets.ISO_8859_1)
        val UTF_32 = ImmutableCharset(kotlin.text.Charsets.UTF_32)
        val GBK = tryLoadCharset("GBK")
        val FOLLOW_SYSTEM = object : MutableCharset("follow system") {
            override fun charset(): Charset {
                return Charset.defaultCharset()
            }
        }

        fun forName(charset: String): Charsets? {
            return SUPPORTED_CHARSETS.firstOrNull { it.displayName() == charset }
                    ?: tryLoadCharset(charset)
        }

        private fun tryLoadCharset(charset: String): Charsets? {
            return try {
                ImmutableCharset(Charset.forName(charset))
            } catch (e: Exception) {
                null
            }
        }

        val SUPPORTED_CHARSETS: Array<Charsets>

        init {
            val charsets = ArrayList<Charsets>()
            charsets.add(FOLLOW_SYSTEM)
            charsets.add(UTF_8)
            GBK?.let { charsets.add(it) }
            charsets.add(US_ASCII)
            charsets.add(UTF_16)
            charsets.add(ISO_8859_1)
            charsets.add(UTF_32)
            SUPPORTED_CHARSETS = charsets.toTypedArray()
        }
    }

    abstract fun charset(): Charset

    abstract fun displayName(): String

    override fun toString(): String {
        return displayName()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        return charset() == (other as Charsets).charset()
    }

    override fun hashCode(): Int {
        return charset().hashCode()
    }


    class ImmutableCharset(private val charset: Charset) : Charsets() {

        override fun charset(): Charset {
            return this.charset
        }

        override fun displayName(): String {
            return charset().displayName()
        }

    }

    abstract class MutableCharset(private val displayName: String?) : Charsets() {

        override fun displayName(): String {
            return displayName ?: charset().displayName()
        }
    }
}