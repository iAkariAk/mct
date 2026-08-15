package mct.cli

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

actual fun createLogDispatcher(): CoroutineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()