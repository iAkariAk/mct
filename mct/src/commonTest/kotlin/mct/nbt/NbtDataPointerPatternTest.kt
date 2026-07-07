package mct.nbt

import io.kotest.core.spec.style.FreeSpec
import mct.pointer.match
import mct.pointer.notMatch
import mct.pointer.test

class NbtDataPointerPatternTest : FreeSpec({
    "BuiltinNbtPatterns BuiltinSet" {
        listOf(
            match(">#display>#Name", "item display Name"),
            match(">#display>#Lore", "item display Lore"),
            match(">#components>#minecraft:lore", "lore component"),
            match(">#components>#minecraft:lore>2>#raw", "lore component raw entry"),
            match(">#components>#minecraft:written_book_content>#pages", "written book pages"),
            match(">#components>#minecraft:writable_book_content>#pages>0>#raw", "writable book pages raw"),
            match(">#>#Entities>0>#CustomName", "entity CustomName"),
            match(">#>#SpawnData>#entity>#CustomName", "spawner entity CustomName"),
            match(">#>#block_entities>5>#front_text>#messages>0>#raw", "sign front_text messages"),
            match(">#>#block_entities>5>#back_text>#filtered_messages", "sign back_text filtered messages"),
            match(">#>#block_entities>3>#CustomName", "block entity CustomName"),
            match(">#>#block_entities>0>#SpawnPotentials>0>#data>#entity>#Passengers>0>#CustomName", "entities' Passengers tag"),
            match(">#>#block_entities>0>#SpawnPotentials>0>#data>#entity>#Passengers>0>#Passengers>0>#Passengers>0>#CustomName", "nested entities' Passengers tag"),
            match(">#>#block_entities>0>#Bees>1>#EntityData>#CustomName", "beehive bee entity CustomName"),
            match(
                ">#>#block_entities>0>#normal_config>#spawn_data>#entity>#CustomName",
                "trial spawner normal_config entity CustomName"
            ),
            match(
                ">#>#block_entities>0>#ominous_config>#spawn_data>#entity>#CustomName",
                "trial spawner ominous_config entity CustomName"
            ),
            match(">#Book>#tag>#title", "book title in tag"),
            match(">#Book>#tag>#author", "book author in tag"),
            notMatch(">#unrelated>#path>#here", "unrelated path"),
            match(">#>#Entities>0>#raw_text", "entity raw_text"),
            match(">#>#block_entities>0>#components>#minecraft:custom_name", "block entity component custom_name"),
            match(
                ">#>#block_entities>0>#components>#minecraft:custom_name>#raw",
                "block entity component custom_name raw"
            ),
            match(">#>#block_entities>0>#components>#minecraft:item_name", "block entity component item_name"),
            match(
                ">#>#block_entities>0>#components>#minecraft:item_name>#raw",
                "block entity component item_name raw"
            ),
            match(
                ">#>#Level>#TileEntities>5>#Book>#tag>#pages",
                "legacy book pages"
            ),
            match(
                ">#>#Level>#TileEntities>1>#Text2",
                "legacy sign"
            ),
            match(
                ">#>#Level>#TileEntities>4>#CustomName",
                "legacy custom tile name"
            )
        ).test(BuiltinNbtPatterns)
    }
})
