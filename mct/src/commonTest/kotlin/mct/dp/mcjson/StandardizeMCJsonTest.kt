package mct.dp.mcjson

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.FreeSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

class StandardizeMCJsonTest : FreeSpec({
    "should work" {
        val json = """
            {"pools":[{"rolls":1,"entries":[
            {"type":"minecraft:item","name":"minecraft:ghast_tear","functions":[{"function":"set_components","components":{"lore":['{"text":"Hello","color":"aqua"}']}}]},
            {"type":"minecraft:item","name":"minecraft:blaze_powder","functions":[{"function":"set_components","components":{"custom_name":'{"text":"Warmth"}'}}]},
            {"type":"minecraft:item","name":"minecraft:snowball","functions":[{"function":"set_components","components":{"lore":['{"text":"Line1"}','{"text":"Line2"}']}}]}
            ]}]}
        """.trimIndent()
        val std = standardizeMCJson(json)
        val jsonFormat = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        shouldNotThrow<SerializationException> {
            jsonFormat.decodeFromString<JsonElement>(std)
        }
    }
})
