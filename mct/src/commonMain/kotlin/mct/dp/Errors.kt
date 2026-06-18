package mct.dp

import kotlinx.serialization.SerializationException
import mct.MCTError


sealed interface DBError : MCTError

sealed interface BackfillError : DBError {}

sealed interface ExtractError : DBError
sealed interface MCFunctionExtractError : ExtractError

sealed interface MCJsonExtractError : ExtractError {
    data class JsonSyntaxError(val source: String, val filePath: String, val exception: SerializationException) :
        MCJsonExtractError {
        override val message = "($source >> $filePath)${exception.message}"
    }
}

sealed interface NbtExtractError : ExtractError {
    data class NbtDecodeError(val source: String, val filePath: String, val exception: SerializationException) :
        MCJsonExtractError {
        override val message = "($source >> $filePath)${exception.message}"
    }
}