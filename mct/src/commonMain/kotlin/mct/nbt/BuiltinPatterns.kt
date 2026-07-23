@file:Suppress("FunctionName")

package mct.nbt

import mct.pointer.ComponentPatterns
import mct.pointer.PatternSet
import mct.pointer.RegexPattern
import mct.pointer.RightPattern

val BuiltinNbtPatterns = PatternSet {
    dependsOn(ComponentPatterns)

    +RightPattern(">#CustomName")

    // --- Item Display & Lore (Legacy/General) ---
    +RightPattern("#display>#Name")                   // Item custom name
    +RightPattern("#display>#Lore")                   // Item lore lines

    // Nested text in data components: instrument.description (text component)
    +RegexPattern("""#instrument>#description$""")
    // Nested text in data components: attribute_modifiers[].display.value (text component)
    +RegexPattern("""#attribute_modifiers>#modifiers>\d+>#display>#value$""")

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

    // legacy
    +RegexPattern(""">#TileEntities>\d+>#Text\d$""")
    +RegexPattern(""">#tag>#(pages|title|author)$""")

    // --- Block Entities (Signs, Containers, Spawners) ---
    // 1. Signs (Front & Back)
    +RegexPattern(""">#block_entities>\d+>#(front|back)_text>#(filtered_)?messages(>\d+>#raw)?$""")

    // 2. Command Blocks
    // 'LastOutput' contains command feedback/error text (user-visible)
    // Note: 'Command' field is handled separately as Type.Command in Extract.kt
    +RegexPattern(""">#block_entities>\d+>#LastOutput$""")
    // Note: Translating 'Command' is handled specially.

    +RegexPattern(""">#block_entities>\d+>#description$""")

    // --- Map Data ---
    // If scanning 'data/map_xxx.dat' files (though usually in separate folder)
    +RightPattern("#banners>#name")                   // Names of marked banners on maps

    // --- Text Display Entity Fields ---
    // raw_text is the rendered plain-text form of a text display's JSON component
    +RegexPattern(""">#Entities>\d+>#raw_text$""")
}