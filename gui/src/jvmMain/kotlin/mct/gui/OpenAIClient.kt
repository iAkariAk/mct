package mct.gui

import com.aallam.openai.client.OpenAI

suspend fun OpenAI.listModels(): List<String> =
    models().map { it.id.id }.sorted()
