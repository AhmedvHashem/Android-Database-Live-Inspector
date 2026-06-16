package dev.ahmedvhashem.databaseliveinspector.agent.capture

/**
 * Keeps Room's own bookkeeping tables out of the capture stream while still letting the
 * underlying app query execute normally.
 */
internal object SqlTableFilter {

    private const val EXCLUDED_PREFIX = "room_table_"
    private val tableReferenceKeywords = setOf(
        "from",
        "join",
        "update",
        "into",
        "table",
        "trigger",
        "index",
        "view",
        "drop",
    )
    private val dropObjectTypes = setOf("table", "trigger", "index", "view")

    fun referencesExcludedTable(sql: String): Boolean {
        val tokens = SqlTokenizer(sql).tokens()
        return tokens.withIndex().any { (index, token) ->
            val keyword = token.text.lowercase()
            token.kind == TokenKind.Identifier &&
                keyword in tableReferenceKeywords &&
                nextTableName(tokens, index + 1, keyword)
                    ?.startsWith(EXCLUDED_PREFIX, ignoreCase = true) == true
        }
    }

    private fun nextTableName(tokens: List<Token>, startIndex: Int, keyword: String): String? {
        var index = startIndex
        if (keyword == "drop" && tokens.getOrNull(index)?.text?.lowercase() in dropObjectTypes) {
            index++
        }
        if (tokens.getOrNull(index)?.text?.equals("or", ignoreCase = true) == true) {
            index += 2 // UPDATE OR REPLACE/IGNORE/ABORT/FAIL/ROLLBACK table_name ...
        }
        if (tokens.getOrNull(index)?.text?.equals("if", ignoreCase = true) == true) {
            index++ // CREATE/DROP object IF [NOT] EXISTS object_name ...
            if (tokens.getOrNull(index)?.text?.equals("not", ignoreCase = true) == true) {
                index++
            }
            if (tokens.getOrNull(index)?.text?.equals("exists", ignoreCase = true) == true) {
                index++
            }
        }

        var tableName: String? = null
        var expectIdentifier = true
        while (index < tokens.size) {
            val token = tokens[index]
            if (expectIdentifier) {
                if (token.kind != TokenKind.Identifier) return tableName
                tableName = token.text
                expectIdentifier = false
                index++
            } else {
                if (token.kind == TokenKind.Symbol && token.text == ".") {
                    expectIdentifier = true
                    index++
                } else {
                    return tableName
                }
            }
        }
        return tableName
    }

    private enum class TokenKind {
        Identifier,
        Symbol,
    }

    private data class Token(
        val kind: TokenKind,
        val text: String,
    )

    private class SqlTokenizer(private val sql: String) {

        fun tokens(): List<Token> {
            val result = mutableListOf<Token>()
            var index = 0
            while (index < sql.length) {
                index = when {
                    sql[index].isWhitespace() -> index + 1
                    sql.startsWith("--", index) -> skipLineComment(index + 2)
                    sql.startsWith("/*", index) -> skipBlockComment(index + 2)
                    sql[index] == '\'' -> skipSingleQuotedString(index + 1)
                    sql[index] == '"' -> readQuotedIdentifier(index + 1, '"', result)
                    sql[index] == '`' -> readQuotedIdentifier(index + 1, '`', result)
                    sql[index] == '[' -> readQuotedIdentifier(index + 1, ']', result)
                    sql[index].isIdentifierStart() -> readIdentifier(index, result)
                    else -> {
                        result += Token(TokenKind.Symbol, sql[index].toString())
                        index + 1
                    }
                }
            }
            return result
        }

        private fun readIdentifier(start: Int, result: MutableList<Token>): Int {
            var index = start + 1
            while (index < sql.length && sql[index].isIdentifierPart()) {
                index++
            }
            result += Token(TokenKind.Identifier, sql.substring(start, index))
            return index
        }

        private fun readQuotedIdentifier(start: Int, endChar: Char, result: MutableList<Token>): Int {
            val text = StringBuilder()
            var index = start
            while (index < sql.length) {
                val char = sql[index]
                if (char == endChar) {
                    if (index + 1 < sql.length && sql[index + 1] == endChar) {
                        text.append(endChar)
                        index += 2
                    } else {
                        result += Token(TokenKind.Identifier, text.toString())
                        return index + 1
                    }
                } else {
                    text.append(char)
                    index++
                }
            }
            return sql.length
        }

        private fun skipSingleQuotedString(start: Int): Int {
            var index = start
            while (index < sql.length) {
                if (sql[index] == '\'') {
                    index++
                    if (index < sql.length && sql[index] == '\'') {
                        index++
                    } else {
                        return index
                    }
                } else {
                    index++
                }
            }
            return sql.length
        }

        private fun skipLineComment(start: Int): Int {
            var index = start
            while (index < sql.length && sql[index] != '\n') {
                index++
            }
            return index
        }

        private fun skipBlockComment(start: Int): Int {
            var index = start
            while (index + 1 < sql.length && !sql.startsWith("*/", index)) {
                index++
            }
            return (index + 2).coerceAtMost(sql.length)
        }

        private fun Char.isIdentifierStart(): Boolean =
            this == '_' || isLetter()

        private fun Char.isIdentifierPart(): Boolean =
            this == '_' || this == '$' || isLetterOrDigit()
    }
}
