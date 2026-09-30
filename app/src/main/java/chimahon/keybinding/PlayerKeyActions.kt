package chimahon.keybinding

/**
 * The subtitle track [by] steps on from [current], going through [trackIds] and then off (-1),
 * either way round. [other] is the track the other subtitle slot shows, which is skipped.
 */
fun nextTrackId(trackIds: List<Int>, current: Int, other: Int, by: Int = 1): Int {
    val choices = listOf(-1) + trackIds.filter { it != other }
    return choices[Math.floorMod(choices.indexOf(current) + by, choices.size)]
}

/**
 * Splits a command typed the way input.conf takes it into the arguments mpv wants. Single and
 * double quotes keep spaces together, and a backslash escapes inside double quotes.
 */
fun tokenizeMpvCommand(command: String): List<String> {
    val tokens = mutableListOf<String>()
    val token = StringBuilder()
    var inToken = false
    var quote: Char? = null
    var index = 0
    while (index < command.length) {
        val char = command[index]
        when {
            quote == null && char.isWhitespace() -> {
                if (inToken) tokens += token.toString()
                token.clear()
                inToken = false
            }
            quote == null && (char == '"' || char == '\'') -> {
                quote = char
                inToken = true
            }
            char == quote -> quote = null
            quote == '"' && char == '\\' && index + 1 < command.length -> {
                index++
                token.append(command[index])
            }
            else -> {
                token.append(char)
                inToken = true
            }
        }
        index++
    }
    if (inToken) tokens += token.toString()
    return tokens
}
