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

private fun shouldBeParsed(@Language("json") json: String) {
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

    "escape single quote" {
        @Suppress("JsonStandardCompliance")
        shouldBeParsed(
            """
            'Ma\'am \\Ugh'
        """.trimIndent()
        )
    }

    "nested json" {
        shouldBeParsed(
            """
            {
              "function": "set_nbt",
              "tag": "{display:{Name:'{\"text\":\"Aurastaff of Permafrost\",\"color\":\"green\",\"italic\":\"false\",\"underlined\":\"true\"}',Lore:['{\"text\":\"Loe and Lai were once the greatest of \"}','{\"text\":\"friends, and together designed many powerful\"}','{\"text\":\"devices for the Tehrmari with divine magic\"}','{\"text\":\"from the fabled forge, Soletta. Though few in\"}','{\"text\":\"number and no longer in production, these\"}','{\"text\":\"staves are immaculately maintained and \"}','{\"text\":\"priceless to the residents of Lo\\'Dahr.\"}','{\"text\":\" \"}','{\"text\":\"Selective Hypothermia\",\"color\":\"green\",\"italic\":\"false\"}','{\"text\":\"When placed, prevents naturally spawning enemies\",\"color\":\"dark_gray\"}','{\"text\":\"from spawning within a 32-block radius around\",\"color\":\"dark_gray\"}','{\"text\":\"itself. Does not work in the overworld.\",\"color\":\"dark_gray\"}','{\"text\":\"\"}','{\"text\":\"Trinket\",\"color\":\"green\",\"italic\":\"false\"}']},WardStaff:1b,CustomModelData:810001,EntityTag:{id:marker,Tags:[\"ward_staff_place\"]}}"
            }
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
