@file:Suppress("FunctionName")

package mct.dp.mcjson

import mct.pointer.PatternSet
import mct.pointer.RegexPattern
import mct.pointer.RightPattern

val BuiltinMCJPatterns = PatternSet {
    // --- Item Components (Modern 1.20.5+ System) ---
    +RightPattern("#components>#custom_name")         // Custom item names
    +RegexPattern("""#components>#lore$""")        // List of item lore lines

    // --- Advancements ---
    +RightPattern("#display>#title")                  // Advancement title
    +RightPattern("#display>#description")            // Advancement description

    // --- Legacy Item Display (Pre-1.20.5 Compatibility) ---
    +RightPattern("#display>#Name")                   // Legacy capitalized 'Name'
    +RegexPattern("""#display>#Lore$""")          // Legacy capitalized 'Lore' array

    // --- Entities & Block Entities ---
    +RightPattern("#CustomName")                      // General custom name for entities/mobs

    // --- Signs (Front & Back) ---
    // Matches 4 lines of text for both sides: front_text/back_text > messages > 0..3
    +RegexPattern("""#(front|back)_text>#messages$""")

    // --- Written Books ---
    +RegexPattern("""#pages$""")                  // Individual book pages
    +RightPattern("#title")                           // Book title
    +RightPattern("#author")                          // Book author

    // --- Painting Variants (title/author as text components) ---
    // Painting variants store title/author as text components:
    // {"title": {"text": "Alban"}, "author": {"text": "Kristoffer Zetterstrand"}}
    +RegexPattern("""#(?:title|author)>#(?:text|translate|fallback)$""")

    // --- Loot Tables & Item Modifiers ---
    // set_name / set_custom_name: {"text": "..."} / {"translate": "..."}
    +RegexPattern("""#functions>\d+>#name>#text$""")
    +RegexPattern("""#functions>\d+>#name>#translate$""")
    +RegexPattern("""#functions>\d+>#name>#fallback$""")
    // set_lore entries
    +RegexPattern("""#functions>\d+>#lore>\d+>#text$""")
    +RegexPattern("""#functions>\d+>#lore>\d+>#translate$""")
    +RegexPattern("""#functions>\d+>#lore>\d+>#fallback$""")
    // set_attributes with custom names
    +RegexPattern("""#functions>\d+>#entity>#name>#text$""")
    +RegexPattern("""#functions>\d+>#entity>#name>#translate$""")
    +RegexPattern("""#functions>\d+>#entity>#name>#fallback$""")
    // set_attributes modifier names (for attribute-specific custom names)
    +RegexPattern("""#functions>\d+>#modifiers>\d+>#name>#(?:text|translate|fallback)$""")

    // --- Jukebox Songs (1.21+) ---
    // Song title/description as plain string, e.g. "Cat - C418"
    +RightPattern("#description")

    // --- Trim Patterns/Materials & Banner Patterns (1.21+) ---
    // Description as text component: {"text": "..."} / {"translate": "..."} / {"fallback": "..."}
    +RegexPattern("""#description>#(?:text|translate|fallback)$""")
}