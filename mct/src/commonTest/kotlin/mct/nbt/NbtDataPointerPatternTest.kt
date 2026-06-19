package mct.nbt

import arrow.core.partially2
import io.kotest.core.spec.style.FreeSpec
import mct.pointer.shouldMatch
import mct.pointer.shouldNotMatch

class NbtDataPointerPatternTest : FreeSpec({
    val shouldMatch = ::shouldMatch.partially2(BuiltinNbtPatterns)
    val shouldNotMatch = ::shouldNotMatch.partially2(BuiltinNbtPatterns)

    "BuiltinNbtPatterns" - {
        "match item display Name" {
            shouldMatch(">#display>#Name")
        }

        "match item display Lore" {
            shouldMatch(">#display>#Lore")
        }

        "match lore component with index" {
            shouldMatch(">#components>#minecraft:lore>2")
        }

        "match lore component with raw" {
            shouldMatch(">#components>#minecraft:lore>2>#raw")
        }

        "match written book pages" {
            shouldMatch(">#components>#minecraft:written_book_content>#pages>0")
        }

        "match writable book pages" {
            shouldMatch(">#components>#minecraft:writable_book_content>#pages>0>#raw")
        }

        "match entity CustomName" {
            shouldMatch(">#>#Entities>0>#CustomName")
        }

        "match spawner entity CustomName" {
            shouldMatch(">#>#SpawnData>#entity>#CustomName")
        }

        "match sign front_text messages" {
            shouldMatch(">#>#block_entities>5>#front_text>#messages>0>#raw")
        }

        "match sign back_text filtered_messages" {
            shouldMatch(">#>#block_entities>5>#back_text>#filtered_messages>3")
        }

        "match block entity CustomName" {
            shouldMatch(">#>#block_entities>3>#CustomName")
        }

        "match beehive bee entity CustomName" {
            shouldMatch(">#>#block_entities>0>#Bees>1>#EntityData>#CustomName")
        }

        "match trial spawner normal_config entity CustomName" {
            shouldMatch(">#>#block_entities>0>#normal_config>#spawn_data>#entity>#CustomName")
        }

        "match trial spawner ominous_config entity CustomName" {
            shouldMatch(">#>#block_entities>0>#ominous_config>#spawn_data>#entity>#CustomName")
        }

        "match book title in tag" {
            shouldMatch(">#Book>#tag>#title")
        }

        "match book author in tag" {
            shouldMatch(">#Book>#tag>#author")
        }

        "not match unrelated path" {
            shouldNotMatch(">#unrelated>#path>#here")
        }

        "match entity raw_text" {
            shouldMatch(">#>#Entities>0>#raw_text")
        }

        "match block entity component custom_name" {
            shouldMatch(">#>#block_entities>0>#components>#minecraft:custom_name")
        }

        "match block entity component custom_name with raw" {
            shouldMatch(">#>#block_entities>0>#components>#minecraft:custom_name>#raw")
        }

        "match block entity component item_name" {
            shouldMatch(">#>#block_entities>0>#components>#minecraft:item_name")
        }

        "match block entity component item_name with raw" {
            shouldMatch(">#>#block_entities>0>#components>#minecraft:item_name>#raw")
        }
    }
})
