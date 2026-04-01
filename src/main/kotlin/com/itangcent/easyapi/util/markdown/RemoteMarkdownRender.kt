package com.itangcent.easyapi.util.markdown

import java.net.HttpURLConnection
import java.net.URI

class RemoteMarkdownRender(private val url: String) : MarkdownRender {
    override fun render(markdown: String): String {
        val connection = (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            setRequestProperty("Accept", "text/html, text/plain, */*")
        }

        connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(markdown)
        }

        val code = connection.responseCode
        val input = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = input?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""

        if (code !in 200..299) {
            throw IllegalStateException("Remote markdown render failed: $code")
        }

        return body
    }
}
