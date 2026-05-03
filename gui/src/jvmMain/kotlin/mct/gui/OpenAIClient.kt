package mct.gui

import com.aallam.openai.client.OpenAI
import mct.extra.ai.ChatCompletionCall

var openAIClient: OpenAI? = null
var chatCompletionCall: ChatCompletionCall? = null

suspend fun OpenAI.listModels(): List<String> =
    models().map { it.id.id }.sorted()

