
package mct.dp.mcjson

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FreeSpec
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.intellij.lang.annotations.Language

private val J = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

private fun shouldBeParsed(@Language("json")  json: String) {
    val std = standardizeMCJson(json)

    withClue(std) {
        shouldNotThrow<SerializationException> {
            J.decodeFromString<JsonElement>(std)
        }
    }
}


class StandardizeMCJsonTest : FreeSpec({
    "single quote" {
        @Suppress("JsonStandardCompliance")
        shouldBeParsed("'hi'")
    }

    "single quote in double quote" {
        shouldBeParsed(
            """
            "{Name: 'yachiyo'}"
        """.trimIndent()
        )
    }

    "should work" {
        @Suppress("JsonStandardCompliance")
        shouldBeParsed(
            """
    {
        "pools": [
            {
                "rolls": 1,
                "entries": [
                    {
                        "type": "minecraft:item",
                        "name": "minecraft:ghast_tear",
                        "functions": [
                            {
                                "function": "set_components",
                                "components": {
                                    "lore": ['{"text": "Hello", "color": "aqua"}']
                                }
                            }
                        ]
                    },
                    {
                        "type": "minecraft:item",
                        "name": "minecraft:blaze_powder",
                        "functions": [
                            {
                                "function": "set_components",
                                "components": {
                                    "custom_name":'{"text": "Warmth"}'
                                }
                            }
                        ]
                    },
                    {
                        "type": "minecraft:item",
                        "name": "minecraft:snowball",
                        "functions": [
                            {
                                "function": "set_components",
                                "components": {
                                    "lore": ['{"text": "Line1"}','{"text": "Line2"}'
                                    ]
                                }
                            }
                        ]
                    }
                ]
            }
        ]
    }
            """
        )
    }
})
