package mct.cli

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

actual fun createLogDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)