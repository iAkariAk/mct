@file:Suppress("FunctionName")

package mct.nbt

import mct.pointer.PatternSet
import mct.pointer.RegexPattern
import mct.pointer.RightPattern

val BuiltinNbtPatterns = PatternSet {
    // --- Item Display & Lore (Legacy/General) ---
    +RightPattern("#display>#Name")                   // Item custom name
    +RightPattern("#display>#Lore")                   // Item lore lines


    // --- Modern Item/Entity Components (1.20.5+) ---
    // In region files, these are often nested within an item's or entity's 'components' tag
    // Entity data components also use the same #components>#minecraft:* path structure
    +RegexPattern("#components>#minecraft:custom_name(>#raw)?$")
    +RegexPattern("#components>#minecraft:item_name(>#raw)?$")
    +RegexPattern("#components>#minecraft:text_display(>#raw)?$")
    +RegexPattern("#components>#minecraft:description(>#raw)?$")
    +RegexPattern("""#components>#minecraft:lore(>\d+>#raw)?$""")
    +RegexPattern("""#components>#minecraft:written_book_content>#(?:pages|title|author)(?:>\d+>#(?:raw|filtered))?$""")
    +RegexPattern("""#components>#minecraft:writable_book_content>#pages(?:>\d+>#(?:raw|filtered))?$""")
    +RegexPattern("""#components>#minecraft:custom_name(>#raw)?$""")
    // Nested text in data components: instrument.description (text component)
    +RegexPattern("""#instrument>#description(?:>#(?:text|translate|fallback))?$""")
    // Nested text in data components: attribute_modifiers[].display.value (text component)
    +RegexPattern("""#attribute_modifiers>#modifiers>\d+>#display>#value(?:>#(?:text|translate|fallback))?$""")

    // Display entities (refer to https://zh.minecraft.wiki/w/%E5%B1%95%E7%A4%BA%E5%AE%9E%E4%BD%93#%E5%AE%9E%E4%BD%93%E6%95%B0%E6%8D%AE)
    +RegexPattern(""">#Entities>\d+>#text$""")
    // description (e.g. entity data component minecraft:description stored as direct field)
    +RegexPattern(""">#Entities>\d+>#description$""")

    // --- Written Books (Nested in Item Tags) ---
    listOf(
        "title",                // Book title
        "author",               // Book author
        "pages",
        "display",
        "filtered_pages",       // Censored/Filtered pages
        "filtered_title",       // Censored/Filtered title
    ).forEach {
        +RightPattern(">#Book>#tag>#$it")
    }
    +RegexPattern(""">#TileEntities>\d+>#Text\d$""")
    +RegexPattern(""">#TileEntities>\d+>#CustomName$""")
    +RegexPattern(""">#tag>#(pages|title|author)$""")

    // --- Entities (Mobs, Armor Stands, etc.) ---
    // Matches CustomName for all entities stored in the chunk
    // Also matches spawner entities (SpawnData / SpawnPotentials) and trial spawner configs
    +RegexPattern("""(?:>#Entities>\d+|(?:>#SpawnData|>#SpawnPotentials>\d+>#data|(?:>#normal_config|>#ominous_config)>#spawn_data)>#entity)>#CustomName$""")

    // --- Block Entities (Signs, Containers, Spawners) ---
    // 1. Signs (Front & Back)
    +RegexPattern(""">#block_entities>\d+>#(front|back)_text>#(filtered_)?messages(>\d+>#raw)?$""")

    // 2. Container Names (Chests, Shulker Boxes, Hoppers)
    // These use 'CustomName' at the block entity root
    +RegexPattern(""">#block_entities>\d+>#CustomName$""")

    // 3. Command Blocks
    // 'CustomName' is the name shown in chat, 'Command' is the actual logic
    +RegexPattern(""">#block_entities>\d+>#CustomName$""")
    // 'LastOutput' contains command feedback/error text (user-visible)
    // Note: 'Command' field is handled separately as Type.Command in Extract.kt
    +RegexPattern(""">#block_entities>\d+>#LastOutput$""")
    // Note: Translating 'Command' is handled specially.

    // 4. Spawners
    // Potential custom names for spawned entities inside a spawner
    +RegexPattern(""">#block_entities>\d+>#SpawnData>#entity>#CustomName$""")

    // 5. Beehives / Bee Nests
    // CustomName for bees (or other mobs) stored inside a beehive block entity
    +RegexPattern(""">#block_entities>\d+>#Bees>\d+>#EntityData>#CustomName$""")

    // --- Map Data ---
    // If scanning 'data/map_xxx.dat' files (though usually in separate folder)
    +RightPattern("#banners>#name")                   // Names of marked banners on maps

    // --- Text Display Entity Fields ---
    // raw_text is the rendered plain-text form of a text display's JSON component
    +RegexPattern(""">#Entities>\d+>#raw_text$""")

    // --- Block Entity Data Components (1.20.5+) ---
    // Modern component-based custom names on block entities
    // Note: entity-level components are covered by the existing #minecraft:custom_name pattern
    // which doesn't have ^ anchor and matches anywhere in the path
    // description on block entities (either direct field or component)
    +RegexPattern(""">#block_entities>\d+>#description$""")
    +RegexPattern(""">#block_entities>\d+>#components>#minecraft:custom_name(?:>#raw)?$""")
    +RegexPattern(""">#block_entities>\d+>#components>#minecraft:item_name(?:>#raw)?$""")
}