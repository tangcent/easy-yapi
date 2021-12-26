package com.itangcent.idea.plugin.api.export.feign

object FeignTemplate {

    /**
     * Parse variables from a template fragment.
     *
     * @param fragment to parse
     * @return variables
     */
    fun parseVariables(fragment: String): List<String> {
        val variables = ArrayList<String>()
        val tokenizer = ChunkTokenizer(fragment)
        while (tokenizer.hasNext()) {
            val chunk = tokenizer.next()
            if (chunk.startsWith("{")) {
                variables.add(chunk.removePrefix("{").removeSuffix("}"))
            }
        }
        return variables
    }
}

/**
 * Splits a Uri into Chunks that exists inside and outside of an expression, delimited by curly
 * braces "{}". Nested expressions are treated as literals, for example "foo{bar{baz}}" will be
 * treated as "foo, {bar{baz}}". Inspired by Apache CXF Jax-RS.
 */
private class ChunkTokenizer(template: String) {
    private val tokens: MutableList<String> = ArrayList()
    private var index = 0
    operator fun hasNext(): Boolean {
        return tokens.size > index
    }

    operator fun next(): String {
        if (hasNext()) {
            return tokens[index++]
        }
        throw IllegalStateException("No More Elements")
    }

    init {
        var outside = true
        var level = 0
        var lastIndex = 0

        /* loop through the template, character by character */
        var idx = 0
        while (idx < template.length) {
            if (template[idx] == '{') {
                /* start of an expression */
                if (outside) {
                    /* outside of an expression */
                    if (lastIndex < idx) {
                        /* this is the start of a new token */
                        tokens.add(template.substring(lastIndex, idx))
                    }
                    lastIndex = idx

                    /*
                     * no longer outside of an expression, additional characters will be treated as in an
                     * expression
                     */
                    outside = false
                } else {
                    /* nested braces, increase our nesting level */
                    level++
                }
            } else if (template[idx] == '}' && !outside) {
                /* the end of an expression */
                if (level > 0) {
                    /*
                     * sometimes we see nested expressions, we only want the outer most expression
                     * boundaries.
                     */
                    level--
                } else {
                    /* outermost boundary */
                    if (lastIndex < idx) {
                        /* this is the end of an expression token */
                        tokens.add(template.substring(lastIndex, idx + 1))
                    }
                    lastIndex = idx + 1

                    /* outside an expression */outside = true
                }
            }
            idx++
        }
        if (lastIndex < idx) {
            /* grab the remaining chunk */
            tokens.add(template.substring(lastIndex, idx))
        }
    }
}