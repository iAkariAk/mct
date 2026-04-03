@file:Suppress("FunctionName")

package mct.region

import mct.pointer.PatternSet
import mct.pointer.RegexPattern
import mct.pointer.RightPattern

val BuiltinPatterns = PatternSet {
    // --- General Item Display ---
    +RightPattern("#display>#Name")                   // Display name of items in containers
    +RightPattern("#display>#Lore")                   // Lore text of items in containers

    // --- Book Data (Legacy/Storage Format) ---
    +RightPattern("#Book>#tag>#pages")                // Content of written books
    +RightPattern("#Book>#tag>#title")                // Title of written books
    +RightPattern("#Book>#tag>#author")               // Author of written books
    +RightPattern("#Book>#tag>#filtered_pages")       // Chat-filtered book content
    +RightPattern("#Book>#tag>#filtered_title")       // Chat-filtered book title

    // --- Entities ---
    // Matches CustomName for entities stored within the chunk's 'Entities' list
    +RegexPattern("""^>#>#Entities>\d+>#CustomName$""")

    // --- Block Entities (Signs, Containers, etc.) ---
    listOf("front_text", "back_text").forEach { side ->
        // Matches the 'messages' array for sign text (1.20+ format)
        +RegexPattern("^>#>#block_entities>\\d+>#$side>#messages$")

        // Matches chat-filtered versions of sign text
        +RegexPattern("^>#>#block_entities>\\d+>#$side>#filtered_messages$")
    }

}