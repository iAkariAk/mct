package mct.gui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import mct.LoggerLevel
import mct.extra.ai.TOKEN_COUNT_THRESHOLD

object GuiSettings {
    var prettyOutput by mutableStateOf(false)
    var tokenThreshold by mutableIntStateOf(TOKEN_COUNT_THRESHOLD)
    var useStreamApi by mutableStateOf(false)
    var temperature by mutableStateOf<Double?>(null)
    var concurrency by mutableIntStateOf(1)
    var concurrentByKind by mutableStateOf(false)
}

data class LogEntry(
    val level: LoggerLevel?,
    val message: String,
)
