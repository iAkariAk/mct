@file:Suppress("FunctionName")

package mct.dp.mcjson

import mct.pointer.PatternSet
import mct.pointer.RegexPattern
import mct.pointer.RightPattern

val BuiltinPatterns = PatternSet {
    // --- Item Components (Modern 1.20.5+ System) ---
    +RightPattern("#components>#custom_name")         // Custom item names
    +RegexPattern("""#components>#lore>\d+$""")        // List of item lore lines

    // --- Advancements ---
    +RightPattern("#display>#title")                  // Advancement title
    +RightPattern("#display>#description")            // Advancement description

    // --- Legacy Item Display (Pre-1.20.5 Compatibility) ---
    +RightPattern("#display>#Name")                   // Legacy capitalized 'Name'
    +RegexPattern("""#display>#Lore>\d+$""")          // Legacy capitalized 'Lore' array

    // --- Entities & Block Entities ---
    +RightPattern("#CustomName")                      // General custom name for entities/mobs

    // --- Signs (Front & Back) ---
    // Matches 4 lines of text for both sides: front_text/back_text > messages > 0..3
    +RegexPattern("""#(front|back)_text>#messages>\d+$""")

    // --- Written Books ---
    +RegexPattern("""#pages>\d+$""")                  // Individual book pages
    +RightPattern("#title")                           // Book title
    +RightPattern("#author")                          // Book author

    // --- Loot Tables & Item Modifiers ---
    // set_name / set_custom_name: {"text": "..."}
    +RegexPattern("""#functions>\d+>#name>#text$""")
    +RegexPattern("""#functions>\d+>#name>#fallback$""")
    // set_lore entries
    +RegexPattern("""#functions>\d+>#lore>\d+>#text$""")
    +RegexPattern("""#functions>\d+>#lore>\d+>#fallback$""")
    // set_attributes with custom names
    +RegexPattern("""#functions>\d+>#entity>#name>#text$""")
}