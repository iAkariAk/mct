package mct.dp.mcjson

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import mct.Logger
import mct.kit.exportIntoPool
import mct.model.patch.ExtractionContent
import mct.model.patch.replace
import mct.pointer.encodeToString

class MCJStructurePatternTest : FreeSpec({
    context(Logger.Console()) {
        "set_nbt" - {
            val json = """
{
    "pools": [
      {
        "rolls": 1,
        "entries": [
          {
            "type": "minecraft:item",
            "name": "minecraft:spyglass",
            "functions": [
              {
                "function": "minecraft:set_nbt",
                "tag": "{display:{Name:'{\"text\":\"Wayfinder\",\"color\":\"light_purple\",\"italic\":false}',Lore:['{\"text\":\"Wayfinder\",\"color\":\"gray\",\"italic\":false}','{\"text\":\"Unbreakable\",\"color\":\"gray\",\"italic\":false}','{\"text\":\"Enchanted by methods forgotten\",\"color\":\"dark_gray\",\"italic\":true}']},HideFlags:1,wayfinder:1,ancient:1,Enchantments:[{id:\"minecraft:unbreaking\",lvl:1s}]}"
              }
            ]
          }
        ]
      }
    ]
}"""
            val extractions = extractTextFromMCJson(json).toList()

            "assert extraction" {
                extractions.size shouldBe 1
                val extraction = extractions.single()
                extraction.pointer.encodeToString() shouldBe ">#pools>0>#entries>0>#functions>0>#tag"
                extraction.format shouldBe SnbtStr
                val content = extraction.content.shouldBeInstanceOf<ExtractionContent.Structure>()
                content.raw shouldContain "Wayfinder"
                content.contents.size shouldBe 2
                val (name, lore) = content.contents
                name.pointer.encodeToString() shouldBe ">#display>#Name"
                name.content.shouldBeInstanceOf<ExtractionContent.Text>()
                name.format shouldBe SnbtStr // despite 100% JSON syntax, it's in a Snbt Compound
                name.content.content shouldBe "{\"text\":\"Wayfinder\",\"color\":\"light_purple\",\"italic\":false}"
                lore.pointer.encodeToString() shouldBe ">#display>#Lore"
                lore.content.shouldBeInstanceOf<ExtractionContent.Text>()
                lore.content.format shouldBe Nbt
                lore.content.content shouldBe """["{\"text\":\"Wayfinder\",\"color\":\"gray\",\"italic\":false}","{\"text\":\"Unbreakable\",\"color\":\"gray\",\"italic\":false}","{\"text\":\"Enchanted by methods forgotten\",\"color\":\"dark_gray\",\"italic\":true}"]"""

                extractions.exportIntoPool(false).size shouldBe 2
            }
            "replace" {
                val replacements =
                    extractions.map { it.replace { it.replace { it.replace("Wayfinder", "AdachiCatcher") }!! } }

                json.backfillMCJson(replacements) shouldBe """{"pools":[{"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:spyglass","functions":[{"function":"minecraft:set_nbt","tag":"{display:{Name:\"{\\\"text\\\":\\\"AdachiCatcher\\\",\\\"color\\\":\\\"light_purple\\\",\\\"italic\\\":false}\",Lore:[\"{\\\"text\\\":\\\"AdachiCatcher\\\",\\\"color\\\":\\\"gray\\\",\\\"italic\\\":false}\",\"{\\\"text\\\":\\\"Unbreakable\\\",\\\"color\\\":\\\"gray\\\",\\\"italic\\\":false}\",\"{\\\"text\\\":\\\"Enchanted by methods forgotten\\\",\\\"color\\\":\\\"dark_gray\\\",\\\"italic\\\":true}\"]},HideFlags:1,wayfinder:1,ancient:1,Enchantments:[{id:\"minecraft:unbreaking\",lvl:1s}]}"}]}]}]}"""
            }
        }
    }
})