package com.itangcent.common.utils

import java.io.File
import java.io.IOException
import java.math.BigInteger

object FileSizeUtils {

    //-----------------------------------------------------------------------
    //-----------------------------------------------------------------------
    /**
     * Returns the size of the specified file or directory. If the provided
     * [File] is a regular file, then the file's length is returned.
     * If the argument is a directory, then the size of the directory is
     * calculated recursively. If a directory or subdirectory is security
     * restricted, its size will not be included.
     *
     *
     * Note that overflow is not detected, and the return value may be negative if
     * overflow occurs. See [.sizeOfAsBigInteger] for an alternative
     * method that does not overflow.
     *
     * @param file the regular file or directory to return the size
     * of (must not be `null`).
     *
     * @return the length of the file, or recursive size of the directory,
     * provided (in bytes).
     *
     * @throws NullPointerException     if the file is `null`
     * @throws IllegalArgumentException if the file does not exist.
     *
     * @since 2.0
     */
    fun sizeOf(file: File): Long {
        if (!file.exists()) {
            val message = "$file does not exist"
            throw IllegalArgumentException(message)
        }
        return if (file.isDirectory) {
            sizeOfDirectory0(file) // private method; expects directory
        } else {
            file.length()
        }
    }

    /**
     * Returns the size of the specified file or directory. If the provided
     * [File] is a regular file, then the file's length is returned.
     * If the argument is a directory, then the size of the directory is
     * calculated recursively. If a directory or subdirectory is security
     * restricted, its size will not be included.
     *
     * @param file the regular file or directory to return the size
     * of (must not be `null`).
     *
     * @return the length of the file, or recursive size of the directory,
     * provided (in bytes).
     *
     * @throws NullPointerException     if the file is `null`
     * @throws IllegalArgumentException if the file does not exist.
     *
     * @since 2.4
     */
    fun sizeOfAsBigInteger(file: File): BigInteger {
        if (!file.exists()) {
            val message = "$file does not exist"
            throw IllegalArgumentException(message)
        }
        return if (file.isDirectory) {
            sizeOfDirectoryBig0(file) // internal method
        } else {
            BigInteger.valueOf(file.length())
        }
    }

    /**
     * Counts the size of a directory recursively (sum of the length of all files).
     *
     *
     * Note that overflow is not detected, and the return value may be negative if
     * overflow occurs. See [.sizeOfDirectoryAsBigInteger] for an alternative
     * method that does not overflow.
     *
     * @param directory directory to inspect, must not be `null`
     * @return size of directory in bytes, 0 if directory is security restricted, a negative number when the real total
     * is greater than [Long.MAX_VALUE].
     * @throws NullPointerException if the directory is `null`
     */
    fun sizeOfDirectory(directory: File): Long {
        checkDirectory(directory)
        return sizeOfDirectory0(directory)
    }

    // Private method, must be invoked will a directory parameter

    // Private method, must be invoked will a directory parameter
    /**
     * the size of a director
     * @param directory the directory to check
     * @return the size
     */
    private fun sizeOfDirectory0(directory: File): Long {
        val files = directory.listFiles()
                ?: // null if security restricted
                return 0L
        var size: Long = 0
        for (file in files) {
            try {
                if (!FileUtils.isSymlink(file)) {
                    size += sizeOf0(file) // internal method
                    if (size < 0) {
                        break
                    }
                }
            } catch (ioe: IOException) {
                // Ignore exceptions caught when asking if a File is a symlink.
            }
        }
        return size
    }

    // Internal method - does not check existence

    // Internal method - does not check existence
    /**
     * the size of a file
     * @param file the file to check
     * @return the size of the file
     */
    private fun sizeOf0(file: File): Long {
        return if (file.isDirectory) {
            sizeOfDirectory0(file)
        } else {
            file.length() // will be 0 if file does not exist
        }
    }

    /**
     * Counts the size of a directory recursively (sum of the length of all files).
     *
     * @param directory directory to inspect, must not be `null`
     * @return size of directory in bytes, 0 if directory is security restricted.
     * @throws NullPointerException if the directory is `null`
     * @since 2.4
     */
    fun sizeOfDirectoryAsBigInteger(directory: File): BigInteger? {
        checkDirectory(directory)
        return sizeOfDirectoryBig0(directory)
    }

    // Must be called with a directory

    // Must be called with a directory
    /**
     * Finds the size of a directory
     *
     * @param directory The directory
     * @return the size
     */
    private fun sizeOfDirectoryBig0(directory: File): BigInteger {
        val files = directory.listFiles()
                ?: // null if security restricted
                return BigInteger.ZERO
        var size = BigInteger.ZERO
        for (file in files) {
            try {
                if (!FileUtils.isSymlink(file)) {
                    size = size.add(sizeOfBig0(file))
                }
            } catch (ioe: IOException) {
                // Ignore exceptions caught when asking if a File is a symlink.
            }
        }
        return size
    }

    // internal method; if file does not exist will return 0
    /**
     * Returns the size of a file
     * @param fileOrDir The file
     * @return the size
     */
    private fun sizeOfBig0(fileOrDir: File): BigInteger? {
        return if (fileOrDir.isDirectory) {
            sizeOfDirectoryBig0(fileOrDir)
        } else {
            BigInteger.valueOf(fileOrDir.length())
        }
    }

    /**
     * Checks that the given `File` exists and is a directory.
     *
     * @param directory The `File` to check.
     * @throws IllegalArgumentException if the given `File` does not exist or is not a directory.
     */
    private fun checkDirectory(directory: File) {
        require(directory.exists()) { "$directory does not exist" }
        require(directory.isDirectory) { "$directory is not a directory" }
    }
}