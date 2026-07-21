package io.github.lootdev.scdl

object ShellTokenizer {
    fun parse(input: String): List<String> {
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quote: Char? = null
        var escaping = false
        var tokenStarted = false

        input.forEach { character ->
            if (escaping) {
                token.append(character)
                escaping = false
                tokenStarted = true
                return@forEach
            }

            when {
                character == '\\' && quote != '\'' -> {
                    escaping = true
                    tokenStarted = true
                }
                quote != null -> {
                    if (character == quote) quote = null else token.append(character)
                    tokenStarted = true
                }
                character == '\'' || character == '"' -> {
                    quote = character
                    tokenStarted = true
                }
                character.isWhitespace() -> {
                    if (tokenStarted) {
                        result += token.toString()
                        token.setLength(0)
                        tokenStarted = false
                    }
                }
                else -> {
                    token.append(character)
                    tokenStarted = true
                }
            }
        }

        require(!escaping) { "The command ends with an unfinished escape character" }
        require(quote == null) { "The command contains an unclosed quote" }
        if (tokenStarted) result += token.toString()
        return result
    }
}
