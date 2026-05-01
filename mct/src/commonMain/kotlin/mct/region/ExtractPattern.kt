@file:Suppress("FunctionName")

package mct.region

import mct.pointer.PatternSet
import mct.pointer.RegexPattern
import mct.pointer.RightPattern

val BuiltinRegionPatterns = PatternSet {
    // --- Item Display & Lore (Legacy/General) ---
    +RightPattern("#display>#Name")                   // Item custom name
    +RightPattern("#display>#Lore")                   // Item lore lines


    // --- Modern Item Components (1.20.5+) ---
    // In region files, these are often nested within an item's 'components' tag
    +RightPattern("#components>#minecraft:custom_name(>#raw)?$")
    +RegexPattern("""#components>#minecraft:lore>\d+(>#raw)?$""")
    +RegexPattern("""#components>#minecraft:written_book_content>#(pages>\d+|title|author)(>#raw)?$""")
    +RegexPattern("""#components>#minecraft:writable_book_content>#pages>\d+(>#raw)?$""")
    +RegexPattern("""#components>#minecraft:custom_name(>#raw)?$""")



    // --- Written Books (Nested in Item Tags) ---
    listOf(
        "pages",                // Book page content
        "title",                // Book title
        "author",               // Book author
        "filtered_pages",       // Censored/Filtered pages
        "filtered_title",       // Censored/Filtered title
    ).forEach {
        +RightPattern("#Book>#tag>#$it")
    }
    // --- Entities (Mobs, Armor Stands, etc.) ---
    // Matches CustomName for all entities stored in the chunk
    +RegexPattern("""(^>#>#Entities>\d+|(>#SpawnData|>#SpawnPotentials\d+>#data)>#entity)>#CustomName$""")

    // --- Block Entities (Signs, Containers, Spawners) ---
    // 1. Signs (Front & Back)
    +RegexPattern("""^>#>#block_entities>\d+>#(front|back)_text>#(filtered_)?messages>\d+(>#raw)?$""")

    // 2. Container Names (Chests, Shulker Boxes, Hoppers)
    // These use 'CustomName' at the block entity root
    +RegexPattern("""^>#>#block_entities>\d+>#CustomName$""")

    // 3. Command Blocks
    // 'CustomName' is the name shown in chat, 'Command' is the actual logic
    +RegexPattern("""^>#>#block_entities>\d+>#CustomName$""")
    // Note: Translating 'Command' is risky, but sometimes hoverEvent/show_text inside commands needs it
    // +RegexPattern("""^>#>#block_entities>\d+>#Command$""")

    // 4. Spawners
    // Potential custom names for spawned entities inside a spawner
    +RegexPattern("""^>#>#block_entities>\d+>#SpawnData>#entity>#CustomName$""")

    // --- Map Data ---
    // If scanning 'data/map_xxx.dat' files (though usually in separate folder)
    +RightPattern("#banners>#name")                   // Names of marked banners on maps
}