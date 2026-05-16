package mct.util.snbt

enum class SnbtTokenType {
    EOF,
    COMMA,
    COLON,
    LITERAL,
    NUMBER,
    STRING,
    L_BRACE,
    R_BRACE,
    L_PAREN,
    R_PAREN,
    L_BRACKET,
    R_BRACKET,
}

data class SnbtToken(
    val type: SnbtTokenType,
    val indices: IntRange
)

