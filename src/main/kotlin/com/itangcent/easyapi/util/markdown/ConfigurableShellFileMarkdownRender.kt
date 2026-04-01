package com.itangcent.easyapi.util.markdown

class ConfigurableShellFileMarkdownRender(private val command: String) : MarkdownRender {
    override fun render(markdown: String): String {
        val parts = splitCommand(command)
        return ShellFileMarkdownRender(parts).render(markdown)
    }

    private fun splitCommand(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var quote: Char? = null

        for (ch in command) {
            when {
                quote != null && ch == quote -> quote = null
                quote == null && (ch == '"' || ch == '\'') -> quote = ch
                quote == null && ch.isWhitespace() -> {
                    if (current.isNotEmpty()) {
                        tokens.add(current.toString())
                        current.setLength(0)
                    }
                }
                else -> current.append(ch)
            }
        }

        if (current.isNotEmpty()) {
            tokens.add(current.toString())
        }

        return tokens
    }
}
