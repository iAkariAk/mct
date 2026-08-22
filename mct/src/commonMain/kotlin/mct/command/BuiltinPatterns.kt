package mct.command

import mct.model.text.isTextComponentJson
import mct.model.text.isTextComponentSnbt
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.EqualPattern
import mct.pointer.RegexPattern
import mct.pointer.RightPattern
import mct.util.isJson
import mct.util.isNamespacedId

private fun String.isSerializedTextComponent() = isTextComponentJson() || isTextComponentSnbt()

val BuiltinCommandPatterns = PatternSet {
    // ── Plain text message commands (greedy) ──────────────────────
    // say <message>
    // me <action>
    // teammsg <message>
    listOf("say", "me", "teammsg").forEach { cmd ->
        command(cmd) {
            Any() then {
                +GreedyPositions()
            }
        }
    }

    // msg <targets> <message>
    // tell <targets> <message>
    // w <targets> <message>
    listOf("tell", "msg", "w").forEach { cmd ->
        command(cmd) {
            WithSize(2) then {
                +GreedyPositions(2)
            }
        }
    }


    // ── JSON text component commands ─────────────────────────────
    // tellraw <targets> <message>
    // Wiki: /tellraw <targets> <message> — message is a raw JSON text component
    command("tellraw") {
        WithSize(2, strict = true) then {
            +Positions(2 to ArgSelection.TextComponentEntire)
        }
    }

    // title <targets> (title|subtitle|actionbar) <component>
    // Wiki: /title <targets> (title|subtitle|actionbar) <title>
    // <title> is a raw JSON text component.
    // "times" subcommand has 5 args (fadeIn stay fadeOut) — excluded by Matches.
    // "clear" and "reset" have 2 args — excluded by WithSize(3, strict).
    command("title") {
        WithSize(3, strict = true) then {
            Positions(3 to ArgSelection.TextComponentEntire) then {
                Matches("not times") { cmd, _ ->
                    cmd[2].content != "times"
                }
            }
        }
    }

    // dialog show <targets> <dialog>
    // Wiki: /dialog show <targets> <dialog>
    // <dialog> is either a namespaced ID (e.g. minecraft:server_links)
    //   or inline SNBT (e.g. {type:"minecraft:notice",title:"..."}).
    // When SNBT, use SnbtEntire to extract text components within (title, label, etc.)
    // "clear" subcommand has 2 args — excluded by WithSize(3, strict).
    command("dialog") {
        WithSize(3, strict = true) then {
            Positions(3 to ArgSelection.SnbtEntire) then {
                Matches("dialog show") { cmd, arg ->
                    cmd[1].content == "show" && arg.content.startsWith("{")
                }
            }
        }
    }


    // ── bossbar ──────────────────────────────────────────────────
    // bossbar add <id> <displayName>
    command("bossbar") {
        WithSize(3, strict = true) then {
            Positions(3 to ArgSelection.TextComponentEntire) then {
                Matches("bossbar add displayName") { cmd, _ ->
                    cmd[1].content == "add"
                }
            }
        }
    }

    // bossbar set <id> name <component>
    command("bossbar") {
        WithSize(4) then {
            Positions(4 to ArgSelection.TextComponentEntire) then {
                Matches("bossbar name") { cmd, _ ->
                    cmd[1].content == "set" && cmd[3].content == "name"
                }
            }
        }
    }


    // ── scoreboard ───────────────────────────────────────────────
    // scoreboard objectives add <objective> <criteria> [<displayName>]
    // scoreboard objectives modify <objective> displayname <component>
    command("scoreboard") {
        WithSize(5, strict = true) then {
            Positions(5 to ArgSelection.TextComponentEntire) then {
                Matches("objective add/modify displayname") { cmd, _ ->
                    cmd[1].content == "objectives" && (
                            cmd[2].content == "add" ||
                                    (cmd[2].content == "modify" && cmd[4].content == "displayname")
                            )
                }
            }
        }
    }

    // scoreboard objectives modify <objective> numberformat fixed <component>
    command("scoreboard") {
        WithSize(6, strict = true) then {
            Positions(6 to ArgSelection.TextComponentEntire) then {
                Matches("objective numberformat fixed") { cmd, _ ->
                    cmd[1].content == "objectives" &&
                            cmd[2].content == "modify" &&
                            cmd[4].content == "numberformat" &&
                            cmd[5].content == "fixed"
                }
            }
        }
    }

    // scoreboard players display name <targets> <objective> <text>
    command("scoreboard") {
        WithSize(6, strict = true) then {
            Positions(6 to ArgSelection.TextComponentEntire) then {
                Matches("player display name") { cmd, _ ->
                    cmd[1].content == "players" &&
                            cmd[2].content == "display" &&
                            cmd[3].content == "name"
                }
            }
        }
    }

    // scoreboard players display numberformat <targets> <objective> fixed <component>
    command("scoreboard") {
        WithSize(7, strict = true) then {
            Positions(7 to ArgSelection.TextComponentEntire) then {
                Matches("player numberformat fixed") { cmd, _ ->
                    cmd[1].content == "players" &&
                            cmd[2].content == "display" &&
                            cmd[3].content == "numberformat" &&
                            cmd[6].content == "fixed"
                }
            }
        }
    }


    // ── team ─────────────────────────────────────────────────────
    // team modify <team> displayName <component>
    command("team") {
        WithSize(4, strict = true) then {
            Positions(4 to ArgSelection.TextComponentEntire) then {
                Matches("team displayName") { cmd, _ ->
                    cmd[1].content == "modify" && cmd[3].content == "displayName"
                }
            }
        }
    }

    // team modify <team> prefix <component>
    // team modify <team> suffix <component>
    command("team") {
        WithSize(4, strict = true) then {
            Positions(4 to ArgSelection.TextComponentEntire) then {
                Matches("team prefix/suffix") { cmd, _ ->
                    cmd[1].content == "modify" &&
                            (cmd[3].content == "prefix" || cmd[3].content == "suffix")
                }
            }
        }
    }


    // ── data ─────────────────────────────────────────────────────
    // data modify (entity|storage) <target> <path> set value <component>
    command("data") {
        WithSize(7, strict = true) then {
            Positions(7 to ArgSelection.TextComponentEntire) then {
                Matches("data modify entity/storage value component") { cmd, arg ->
                    cmd[1].content == "modify" &&
                            (cmd[2].content == "entity" || cmd[2].content == "storage") &&
                            cmd[5].content == "set" &&
                            cmd[6].content == "value" &&
                            arg.content.isSerializedTextComponent()
                }
            }
        }
    }

    // data modify block <pos> <path> set value <component>
    command("data") {
        WithSize(9, strict = true) then {
            Positions(9 to ArgSelection.TextComponentEntire) then {
                Matches("data modify block value component") { cmd, arg ->
                    cmd[1].content == "modify" &&
                            cmd[2].content == "block" &&
                            cmd[7].content == "set" &&
                            cmd[8].content == "value" &&
                            arg.content.isSerializedTextComponent()
                }
            }
        }
    }


    // ── give (item with text components in NBT) ─────────────────
    command("give") {
        WithSize(2) then {
            Positions(2 to ArgSelection.ItemStack).withAry()
        }
    }


    // ── item ─────────────────────────────────────────────────────
    // https://zh.minecraft.wiki/w/%E5%91%BD%E4%BB%A4/item
    // <modifier>: minecraft:loot_modifier to SnbtEntire
    // <item>: minecraft:item_stack to ItemStack
    // <pos>: X Y Z
    // <target> when block: X Y Z
    // <source> when block: X Y Z
    command("item") {
        // item modify (block <pos>|entity <targets>) <slot> <modifier>
        WithSize(5, strict = true) then {
            Positions(5 to ArgSelection.SnbtEntire) then {
                Matches("item modify entity ... modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "modify" && cmd[2].content == "entity" && !arg.content.isNamespacedId()
                }
            }
        }
        WithSize(7, strict = true) then {
            Positions(7 to ArgSelection.SnbtEntire) then {
                Matches("item modify block ... modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "modify" && cmd[2].content == "block" && !arg.content.isNamespacedId()
                }
            }
        }

        // item replace (block <pos>|entity <targets>) <slot> with <item> [<count>]
        WithSize(6) then {
            Positions(6 to ArgSelection.ItemStack) then {
                Matches("item replace entity ... item (item_stack)") { cmd, _ ->
                    cmd[1].content == "replace" && cmd[2].content == "entity" && cmd[5].content == "with"
                }
            }
        }
        WithSize(8) then {
            Positions(8 to ArgSelection.ItemStack) then {
                Matches("item replace block ... item (item_stack)") { cmd, _ ->
                    cmd[1].content == "replace" && cmd[2].content == "block" && cmd[7].content == "with"
                }
            }
        }

        // item replace (block <pos>|entity <targets>) <slot> from (block|entity) <source> <sourceSlot> [<modifier>]
        WithSize(9, strict = true) then {
            Positions(9 to ArgSelection.SnbtEntire) then {
                Matches("item replace entity ...  modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "replace" && cmd[2].content == "entity" &&
                            cmd[5].content == "from" && cmd[6].content == "entity" && !arg.content.isNamespacedId()
                }
            }
        }
        WithSize(11, strict = true) then {
            Positions(11 to ArgSelection.SnbtEntire) then {
                Matches("item replace cross source modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "replace" && (
                            (cmd[2].content == "entity" && cmd[5].content == "from" && cmd[6].content == "block") ||
                                    (cmd[2].content == "block" && cmd[7].content == "from" && cmd[8].content == "entity")
                            ) && !arg.content.isNamespacedId()
                }
            }
        }
        WithSize(13, strict = true) then {
            Positions(13 to ArgSelection.SnbtEntire) then {
                Matches("item replace block ...  modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "replace" && cmd[2].content == "block" &&
                            cmd[7].content == "from" && cmd[8].content == "block" && !arg.content.isNamespacedId()
                }
            }
        }

        // --- 26.3+---

        // item fill (block|entity) <target> <slots> from (block|entity) <source> <sourceSlots> [<modifier>]
        WithSize(9, strict = true) then {
            Positions(9 to ArgSelection.SnbtEntire) then {
                Matches("item fill entity ...  modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "fill" && cmd[2].content == "entity" &&
                            cmd[5].content == "from" && cmd[6].content == "entity" && !arg.content.isNamespacedId()
                }
            }
        }
        WithSize(11, strict = true) then {
            Positions(11 to ArgSelection.SnbtEntire) then {
                Matches("item fill cross source modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "fill" && (
                            (cmd[2].content == "entity" && cmd[5].content == "from" && cmd[6].content == "block") ||
                                    (cmd[2].content == "block" && cmd[7].content == "from" && cmd[8].content == "entity")
                            ) && !arg.content.isNamespacedId()
                }
            }
        }
        WithSize(13, strict = true) then {
            Positions(13 to ArgSelection.SnbtEntire) then {
                Matches("item fill block from block modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "fill" && cmd[2].content == "block" &&
                            cmd[7].content == "from" && cmd[8].content == "block" && !arg.content.isNamespacedId()
                }
            }
        }

        // item fill (block|entity) <target> <slots> with <item> [<count>]
        WithSize(6) then {
            Positions(6 to ArgSelection.ItemStack) then {
                Matches("item fill entity ...  item (ItemStack)") { cmd, arg ->
                    cmd[1].content == "fill" && cmd[2].content == "entity" && cmd[5].content == "with" && !arg.content.isNamespacedId()
                }
            }
        }
        WithSize(8) then {
            Positions(8 to ArgSelection.ItemStack) then {
                Matches("item fill block ...  item (ItemStack)") { cmd, arg ->
                    cmd[1].content == "fill" && cmd[2].content == "block" && cmd[7].content == "with" && !arg.content.isNamespacedId()
                }
            }
        }
        // item modify (block|entity) <target> <slots> <modifier>
        // as the above old
        // item override (block|entity) <target> <slots> from (block|entity) <source> <sourceSlots> [<modifier>]
        WithSize(9, strict = true) then {
            Positions(9 to ArgSelection.SnbtEntire) then {
                Matches("item override entity ... from ... modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "override" && cmd[2].content == "entity" &&
                            cmd[5].content == "from" && cmd[6].content == "entity" && !arg.content.isNamespacedId()
                }
            }
        }
        WithSize(11, strict = true) then {
            Positions(11 to ArgSelection.SnbtEntire) then {
                Matches("item override cross source modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "override" && (
                            (cmd[2].content == "entity" && cmd[5].content == "from" && cmd[6].content == "block") ||
                                    (cmd[2].content == "block" && cmd[7].content == "from" && cmd[8].content == "entity")
                            ) && !arg.content.isNamespacedId()
                }
            }
        }
        WithSize(13, strict = true) then {
            Positions(13 to ArgSelection.SnbtEntire) then {
                Matches("item override block from block modifier (modifier)") { cmd, arg ->
                    cmd[1].content == "override" && cmd[2].content == "block" &&
                            cmd[7].content == "from" && cmd[8].content == "block" && !arg.content.isNamespacedId()
                }
            }
        }
        // item override (block|entity) <target> <slots> with <item> [<count>]
        WithSize(6) then {
            Positions(6 to ArgSelection.ItemStack) then {
                Matches("item override entity ... with ...  item (ItemStack)") { cmd, _ ->
                    cmd[1].content == "override" && cmd[2].content == "entity" && cmd[5].content == "with"
                }
            }
        }
        WithSize(8) then {
            Positions(8 to ArgSelection.ItemStack) then {
                Matches("item override block ... with ...  item (ItemStack)") { cmd, _ ->
                    cmd[1].content == "override" && cmd[2].content == "block" && cmd[7].content == "with"
                }
            }
        }
        // item replace (block|entity) <target> <slots> from (block|entity) <source> <sourceSlots> [<modifier>]
        // as the above old
        // item replace (block|entity) <target> <slots> with <item> [<count>]
        // as the above old
    }

    // https://zh.minecraft.wiki/w/%E5%91%BD%E4%BB%A4/replaceitem
    command("replaceitem") {
        // replaceitem block <position: x y z> slot.container <slotId: int> <itemName: Item> [amount: int] [data: int] [components: json]
        WithSize(10, strict = true) then {
            Positions(10 to ArgSelection.WithInfo(JsonStr)) then {
                Matches("replaceitem block (json)") { cmd, arg ->
                    cmd[1].content == "block" && arg.content.isJson(MCCommandJsonRight)
                }
            }
        }
        // replaceitem block <position: x y z> slot.container <slotId: int> <oldItemHandling: ReplaceMode> <itemName: Item> [amount: int] [data: int] [components: json]
        WithSize(11, strict = true) then {
            Positions(11 to ArgSelection.WithInfo(JsonStr)) then {
                Matches("replaceitem block (json)") { cmd, arg ->
                    cmd[1].content == "block" && arg.content.isJson(MCCommandJsonRight)
                }
            }
        }
        // replaceitem entity <target: target> <slotType: EntityEquipmentSlot> <slotId: int> <itemName: Item> [amount: int] [data: int] [components: json]
        WithSize(8, strict = true) then {
            Positions(8 to ArgSelection.WithInfo(JsonStr)) then {
                Matches("replaceitem entity (json)") { cmd, arg ->
                    cmd[1].content == "entity" && arg.content.isJson(MCCommandJsonRight)
                }
            }
        }
        // replaceitem entity <target: target> <slotType: EntityEquipmentSlot> <slotId: int> <oldItemHandling: ReplaceMode> <itemName: Item> [amount: int] [data: int] [components: json]
        WithSize(9, strict = true) then {
            Positions(9 to ArgSelection.WithInfo(JsonStr)) then {
                Matches("replaceitem entity (json)") { cmd, arg ->
                    cmd[1].content == "entity" && arg.content.isJson(MCCommandJsonRight)
                }
            }
        }
    }


    // ── kick — greedy plain text reason ─────────────────────────
    // Wiki: /kick <targets> [<reason>]
    // <reason> is a "message" type — greedy phrase string, NOT a JSON text component.
    // Entity selectors in the message are substituted with player names.
    command("kick") {
        WithSize(2) then {
            +GreedyPositions(2)
        }
    }


    // ── team add ─────────────────────────────────────────────────
    // team add <team> [<displayName>]
    // displayName is a JSON text component at position 3
    command("team") {
        WithSize(3, strict = true) then {
            Positions(3 to ArgSelection.TextComponentEntire) then {
                Matches("team add") { cmd, _ ->
                    cmd[1].content == "add"
                }
            }
        }
    }


    // ── setblock (NBT data with text components) ─────────────────
    // setblock <pos> <block> [<state>] [<data>]
    // The NBT data at position 5 may contain text components like CustomName
    command("setblock") {
        WithSize(5) then {
            Positions(5 to ArgSelection.SnbtEntire) then {
                Matches("setblock nbt") { _, arg ->
                    arg.content.startsWith("{")
                }
            }
        }
    }


    // ── data merge (NBT with text components) ────────────────────
    // data merge entity <target> <nbt>
    // data merge storage <source> <nbt>
    command("data") {
        And(WithSize(4), Regex("merge (entity|storage)")) then {
            Positions(4 to ArgSelection.SnbtEntire) then {
                Matches("data merge nbt") { _, arg ->
                    arg.content.startsWith("{")
                }
            }
        }
    }

    // data merge block <pos> <nbt>
    command("data") {
        And(WithSize(6), Regex("merge block")) then {
            Positions(6 to ArgSelection.SnbtEntire) then {
                Matches("data merge block nbt") { _, arg ->
                    arg.content.startsWith("{")
                }
            }
        }
    }


    // summon <entity> <pos>*3 [<nbt>]
    command("summon") {
        WithSize(5, strict = true) then {
            Positions(5 to ArgSelection.SnbtEntire).withAry()
        }
    }
}


val BuiltinCommandDataPatterns = mct.pointer.PatternSet {
    dependsOn(BuiltinNbtPatterns)

    +EqualPattern(">#name")
    // ── Display entity text ──────────────────────────────────────
    +RightPattern(">#text")

    // ── CustomName ───────────────────────────────────────────────
    // CustomName text components in NBT (entities, block entities, etc.)
    +RightPattern(">#CustomName")

    // ── Dialog SNBT fields ───────────────────────────────────────
    // /dialog show <targets> {type:"...",title:{...},...}
    // title and external_title are text components
    +RegexPattern("""^>#(?:title|external_title)$""")
    // button labels and tooltips
    +RegexPattern(""">#(?:yes|no|after_action|exit_action|actions>\d+)>#(?:label|tooltip)$""")
    // body contents (plain_message)
    +RegexPattern("""^>#body>\d+>#contents$""")
    // dialogs list in dialog_list type
    +RegexPattern("""^>#dialogs>\d+>#(?:title|external_title)$""")
    // input control labels
    +RegexPattern("""^>#inputs>\d+>#label$""")
    // item description in body items
    +RegexPattern("""^>#body>\d+>#description$""")
}
