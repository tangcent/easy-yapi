package com.itangcent.idea.utils

import java.nio.charset.Charset

/**
 * Constant definitions for the supported [charsets](Charset).
 * Note that these charsets are not guaranteed to be available on all implementation of the Java
 * platform.
 *
 */
abstract class Charsets {

    companion object {

        /**
         * Eight-bit UCS Transformation Format.
         */
        val UTF_8 = ImmutableCharset(kotlin.text.Charsets.UTF_8)

        /**
         * Seven-bit ASCII, a.k.a. ISO646-US, a.k.a. the Basic Latin block of the
         * Unicode character set.
         */
        val US_ASCII = ImmutableCharset(kotlin.text.Charsets.US_ASCII)

        /**
         * Sixteen-bit UCS Transformation Format, byte order identified by an
         * optional byte-order mark.
         */
        val UTF_16 = ImmutableCharset(kotlin.text.Charsets.UTF_16)

        /**
         * ISO Latin Alphabet No. 1, a.k.a. ISO-LATIN-1.
         */
        val ISO_8859_1 = ImmutableCharset(kotlin.text.Charsets.ISO_8859_1)

        /**
         * 32-bit Unicode (or UCS) Transformation Format, byte order identified by an optional byte-order mark
         */
        val UTF_32 = ImmutableCharset(kotlin.text.Charsets.UTF_32)

        /**
         * This charset is not available on some platforms
         */
        val GBK = tryLoadCharset("GBK")

        /**
         * Follows the default charset of this Java virtual machine.
         */
        val FOLLOW_SYSTEM = object : MutableCharset("follow system") {
            override fun charset(): Charset {
                return Charset.defaultCharset()
            }
        }

        /**
         * Returns a charset object for the named charset.
         */
        fun forName(charset: String): Charsets? {
            return SUPPORTED_CHARSETS.firstOrNull {
                it.displayName() == charset || it.charset().name() == charset
            } ?: tryLoadCharset(charset)
        }

        /**
         * Try load the charset object for the special name.
         */
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

    /**
     * Returns this charset's human-readable name for the default locale.
     *
     * <p> The default implementation of this method simply returns this
     * charset's canonical name.  Concrete subclasses of this class may
     * override this method in order to provide a localized display name. </p>
     *
     * @return  The display name of this charset in the default locale
     */
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

    /**
     * These charsets are guaranteed to be available on every implementation of the Java platform.
     */
    class ImmutableCharset(private val charset: Charset) : Charsets() {

        override fun charset(): Charset {
            return this.charset
        }

        /**
         * Returns this charset's human-readable name for the default locale.
         *
         * <p> The default implementation of this method simply returns this
         * charset's canonical name.  Concrete subclasses of this class may
         * override this method in order to provide a localized display name. </p>
         *
         * @return  The display name of this charset in the default locale
         */
        override fun displayName(): String {
            return charset().displayName()
        }

    }

    /**
     * These charsets are not guaranteed to be available on all implementation of the Java platform.
     */
    abstract class MutableCharset(private val displayName: String?) : Charsets() {

        /**
         * Return customized [MutableCharset.displayName] if it is present.
         * otherwise return the displayName of [charset].
         */
        override fun displayName(): String {
            return displayName ?: charset().displayName()
        }
    }
}