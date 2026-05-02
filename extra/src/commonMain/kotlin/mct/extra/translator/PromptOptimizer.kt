package mct.extra.translator

import com.aallam.openai.client.OpenAI
import mct.Env

private val PROMPT =
    "You are a prompt engineering expert. Improve the following prompt for a Minecraft translation AI. Keep all existing rules and structure intact, but make the phrasing clearer, more effective, and more comprehensive. Return ONLY the improved prompt text, no explanations or markdown."

context(env: Env)
suspend fun OpenAI.optimizePrompt(model: String, currentPrompt: String, useStreamApi: Boolean = false): String {
    return chatRaw(model, PROMPT, currentPrompt, useStreamApi).trim()
}
