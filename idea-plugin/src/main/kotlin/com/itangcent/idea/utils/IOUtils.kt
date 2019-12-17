package com.itangcent.idea.utils

import java.io.InputStream

/**
 * - there's some function copy from [com.sun.org.apache.xerces.internal.xinclude.XIncludeTextReader]
 */
object IOUtils {

    fun getEncodingName(stream: InputStream): String? {
        val b4 = ByteArray(4)
        var encoding: String? = null

        // this has the potential to throw an exception
        // it will be fixed when we ensure the stream is rewindable (see note above)
        stream.mark(4)
        val count = stream.read(b4, 0, 4)
        stream.reset()
        if (count == 4) {
            encoding = getEncodingName(b4)
        }

        return encoding
    }

    /**
     * REVISIT: This code is taken from com.sun.org.apache.xerces.internal.impl.XMLEntityManager.
     *          Is there any way we can share the code, without having it implemented twice?
     *          I think we should make it public and static in XMLEntityManager. --PJM
     *
     * Returns the IANA encoding name that is auto-detected from
     * the bytes specified, with the endian-ness of that encoding where appropriate.
     *
     * @param b4    The first four bytes of the input.
     * @return the encoding name, or null if no encoding could be detected
     */
    fun getEncodingName(b4: ByteArray): String? {

        // UTF-16, with BOM
        val b0: Int = (b4[0].toInt() and 0xFF)
        val b1: Int = (b4[1].toInt() and 0xFF)
        if (b0 == 0xFE && b1 == 0xFF) {
            // UTF-16, big-endian
            return "UTF-16BE"
        }
        if (b0 == 0xFF && b1 == 0xFE) {
            // UTF-16, little-endian
            return "UTF-16LE"
        }

        // UTF-8 with a BOM
        val b2: Int = (b4[2].toInt() and 0xFF)
        if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
            return "UTF-8"
        }

        // other encodings
        val b3: Int = (b4[3].toInt() and 0xFF)
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x00 && b3 == 0x3C) {
            // UCS-4, big endian (1234)
            return "ISO-10646-UCS-4"
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x00 && b3 == 0x00) {
            // UCS-4, little endian (4321)
            return "ISO-10646-UCS-4"
        }
        if (b0 == 0x00 && b1 == 0x00 && b2 == 0x3C && b3 == 0x00) {
            // UCS-4, unusual octet order (2143)
            return "ISO-10646-UCS-4"
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x00) {
            // UCS-4, unusual octect order (3412)
            return "ISO-10646-UCS-4"
        }
        if (b0 == 0x00 && b1 == 0x3C && b2 == 0x00 && b3 == 0x3F) {
            // UTF-16, big-endian, no BOM
            // (or could turn out to be UCS-2...
            return "UTF-16BE"
        }
        if (b0 == 0x3C && b1 == 0x00 && b2 == 0x3F && b3 == 0x00) {
            // UTF-16, little-endian, no BOM
            // (or could turn out to be UCS-2...
            return "UTF-16LE"
        }
        if (b0 == 0x4C && b1 == 0x6F && b2 == 0xA7 && b3 == 0x94) {
            // EBCDIC
            // a la xerces1, return CP037 instead of EBCDIC here
            return "CP037"
        }

        // this signals us to use the value from the encoding attribute
        return null
    }
}