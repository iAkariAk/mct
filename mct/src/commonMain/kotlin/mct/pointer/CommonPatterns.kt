package mct.pointer

val ComponentPatterns = PatternSet {
    // --- Modern Item/Entity Components (1.20.5+) ---
    // In region files, these are often nested within an item's or entity's 'components' tag
    // Entity data components also use the same #components>#minecraft:* path structure
    +RegexPattern(">#components>#(minecraft:)?custom_name(>#raw)?$")
    +RegexPattern(">#components>#(minecraft:)?item_name(>#raw)?$")
    +RegexPattern(">#components>#(minecraft:)?text_display(>#raw)?$")
    +RegexPattern(">#components>#(minecraft:)?description(>#raw)?$")
    +RegexPattern(""">#components>#(minecraft:)?lore(>\d+>#raw)?$""")
    +RegexPattern(""">#components>#(minecraft:)?written_book_content>#(?:pages|title|author)(?:>\d+>#(?:raw|filtered))?$""")
    +RegexPattern(""">#components>#(minecraft:)?writable_book_content>#pages(?:>\d+>#(?:raw|filtered))?$""")
    +RegexPattern(""">#components>#(minecraft:)?custom_name(>#raw)?$""")
}