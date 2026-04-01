package com.itangcent.easyapi.util.markdown

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class ShellFileMarkdownRender(private val command: List<String>) : MarkdownRender {
    override fun render(markdown: String): String {
        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(markdown)
        }

        val output = ByteArrayOutputStream()
        process.inputStream.use { input ->
            input.copyTo(output)
        }

        if (!process.waitFor(15, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Markdown render command timed out: ${command.joinToString(" ")}")
        }

        if (process.exitValue() != 0) {
            throw IllegalStateException("Markdown render command failed: ${command.joinToString(" ")}")
        }

        return output.toString(Charsets.UTF_8)
    }
}
