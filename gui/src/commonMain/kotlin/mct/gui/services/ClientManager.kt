package mct.gui.services

import com.aallam.openai.client.OpenAI
import mct.extra.ai.ChatCompletionCall
import org.koin.dsl.module

class ClientManager {
    var openAIClient: OpenAI? = null
    var chatCompletionCall: ChatCompletionCall? = null
}

val apiModule = module {
    single { ClientManager() }
}
