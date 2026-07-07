package mct.dp.mcjson

import io.kotest.core.spec.style.FreeSpec
import mct.pointer.match
import mct.pointer.notMatch
import mct.pointer.test

class MCJDataPointerPatternTest : FreeSpec({
    "BuiltinMCJPatterns BuiltinSet" {
        listOf(
            match(">#components>#custom_name", "custom_name component"),
            match(">#components>#lore", "lore component"),
            match(">#display>#title", "advancement title"),
            match(">#display>#description", "advancement description"),
            match(">#display>#Name", "legacy item Name"),
            match(">#display>#Lore", "legacy item Lore"),
            match(">#CustomName", "entity CustomName"),
            match(">#front_text>#messages", "sign front_text messages"),
            match(">#back_text>#messages", "sign back_text messages"),
            match(">#pages", "book pages"),
            match(">#title", "book title"),
            match(">#author", "book author"),
            match(">#functions>0>#name", "loot table set_name text"),
            match(">#functions>0>#lore", "loot table set_lore text"),
            match(">#functions>0>#modifiers>0>#name", "loot table set_attributes modifier name text"),
            match(">#title", "painting variant title text component"),
            match(">#author", "painting variant author text component"),
            notMatch(">#title>#color", "painting variant title color leaf"),
            notMatch(">#author>#color", "painting variant author color leaf"),
            notMatch(">#title>#extra", "unrelated path under title"),
            notMatch(">#random>#key", "unrelated path"),
            match(">#description", "description as plain string"),
            match(">#description", "description as text component"),
            match(">#display>#description", "description color under display"),
            match(">#exit_action>#tooltip", "dialog"),
            match(">#exit_action>#label", "dialog"),
        ).test(BuiltinMCJPatterns)
    }
})
