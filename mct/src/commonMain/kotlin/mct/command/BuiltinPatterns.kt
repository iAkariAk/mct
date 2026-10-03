package mct.command

import mct.model.text.isTextComponentJson
import mct.model.text.isTextComponentSnbt
import mct.nbt.BuiltinNbtPatterns
import mct.pointer.EqualPattern
import mct.pointer.RegexPattern
import mct.pointer.RightPattern
import mct.util.isJson
import mct.util.isNamespacedId

private fun MCCommand.Arg.isJson() = content.isJson(MCCommandJsonRight)
private fun MCCommand.Arg.isSerializedTextComponent() = content.isTextComponentJson() || content.isTextComponentSnbt()
private fun MCCommand.Arg.mayBeSnbtCompound() = content.startsWith('{') && content.endsWith('}')

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
                    cmd[1].content == "show" && arg.mayBeSnbtCompound()
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
        WithSize(4, strict = true) then {
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

    // legacy: the optional `dataTag` filter is a trailing argument
    // https://minecraft.wiki/w/Scoreboard?oldid=1184804 (the 1.12-era command reference)
    //   scoreboard players tag <entity> add|remove <tagName> [dataTag]              (1.9, 15w32b)
    //   scoreboard players set|add|remove <entity> <objective> <score> [dataTag]    (1.8, 14w10a)
    // 1.13 (17w45a) split `players tag` out to /tag and dropped the `dataTag` filter, so both
    // forms only exist up to 1.12.2: https://minecraft.wiki/w/Commands/scoreboard
    command("scoreboard") {
        WithSizeIn(5..6) then {
            Positions(-1 to ArgSelection.SnbtEntire) then {
                val legacyPlayersSubcommands = setOf("tag", "set", "add", "remove")
                Matches("legacy players dataTag") { cmd, arg ->
                    cmd[1].content == "players" &&
                            cmd[2].content in legacyPlayersSubcommands &&
                            arg.mayBeSnbtCompound()
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

    // blockdata (legacy)
    // https://minecraft.wiki/w/Commands/blockdata
    // blockdata <x> <y> <z> <dataTag> <UserCreator>
    command("blockdata") {
        WithSize(4) then {
            Positions(4 to ArgSelection.SnbtEntire) then {
                Matches { _, arg ->
                    arg.mayBeSnbtCompound()
                }
            }
        }
    }


    // entitydata (legacy)
    // https://minecraft.wiki/w/Commands/entitydata
    // entitydata <entity> <dataTag>
    command("entitydata") {
        WithSize(2) then {
            Positions(2 to ArgSelection.SnbtEntire) then {
                Matches { _, arg ->
                    arg.mayBeSnbtCompound()
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
                            arg.isSerializedTextComponent()
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
                            arg.isSerializedTextComponent()
                }
            }
        }
    }


    // ── give (item with text components in NBT) ─────────────────
    // https://zh.minecraft.wiki/w/%E5%91%BD%E4%BB%A4/give
    // give <targets> <item> [<count>]
    command("give") {
        WithSizeIn(2..3) then {
            Positions(2 to ArgSelection.ItemStack).withAry()
        }

        // legacy: https://minecraft.wiki/w/Commands/give?oldid=1167849
        // give <player> <item> [amount] [data] [dataTag]
        // `dataTag` was added in 1.7.2 (13w36a) and merged into `<item>` in 1.13 (17w45a /
        // flattening), so the trailing-argument form only exists up to 1.12.2.
        WithSizeIn(3..5) then {
            Positions(-1 to ArgSelection.SnbtEntire) then {
                Matches("legacy dataTag") { _, arg ->
                    arg.mayBeSnbtCompound()
                }
            }
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
        WithSizeIn(6..7) then {
            Positions(6 to ArgSelection.ItemStack) then {
                Matches("item replace entity ... item (item_stack)") { cmd, _ ->
                    cmd[1].content == "replace" && cmd[2].content == "entity" && cmd[5].content == "with"
                }
            }
        }
        WithSizeIn(8..9) then {
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
        WithSizeIn(6..7) then {
            Positions(6 to ArgSelection.ItemStack) then {
                Matches("item fill entity ...  item (ItemStack)") { cmd, arg ->
                    cmd[1].content == "fill" && cmd[2].content == "entity" && cmd[5].content == "with" && !arg.content.isNamespacedId()
                }
            }
        }
        WithSizeIn(8..9) then {
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
        WithSizeIn(6..7) then {
            Positions(6 to ArgSelection.ItemStack) then {
                Matches("item override entity ... with ...  item (ItemStack)") { cmd, _ ->
                    cmd[1].content == "override" && cmd[2].content == "entity" && cmd[5].content == "with"
                }
            }
        }
        WithSizeIn(8..9) then {
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

    // https://minecraft.wiki/w/Commands/fill
    command("fill") {
        // fill <from> <to> <block> [outline|hollow|destroy|strict|replace|keep]
        WithSizeIn(7..8) then {
            Positions(7 to ArgSelection.BlockState) then {
                val fillModes = setOf("outline", "hollow", "destroy", "strict", "replace", "keep")
                Matches("arg8 check fillModes") { cmd, _ ->
                    cmd.args.size == 7 || cmd[8].content in fillModes
                }
            }
        }
        // fill <from> <to> <block> replace <filter> [outline|hollow|destroy|strict]
        WithSizeIn(9..10) then {
            Positions(7 to ArgSelection.BlockState) then { // TODO: add BlockPredicate
                val fillModes = setOf("outline", "hollow", "destroy", "strict")

                Matches("arg 8 & arg10 check") { cmd, _ ->
                    cmd[8].content == "replace" && (cmd.args.size == 9 || cmd[10].content in fillModes)
                }
            }
        }

        // legacy: https://minecraft.wiki/w/Commands/fill?oldid=1352716
        //         https://minecraft.wiki/w/Commands/fill?oldid=1173481 (the 1.12.2 syntax)
        // fill <x1> <y1> <z1> <x2> <y2> <z2> <block> [dataValue|state] [oldBlockHandling] [dataTag]
        // 7 mandatory arguments plus up to three optional ones, so the `dataTag` (NBT) can also
        // land on argument 10 — e.g. `fill … end_gateway default destroy {ExactTeleport:1b}`.
        // 1.13 moved the NBT into the `<block>` argument (block_id[block_states]{data_tags}).
        WithSizeIn(8..10) then {
            Positions(-1 to ArgSelection.SnbtEntire) then {
                Matches("latest arg check") { _, arg ->
                    arg.mayBeSnbtCompound()
                }
            }
        }
        // ignore: fill <x1> <y1> <z1> <x2> <y2> <z2> <block> replace [replaceTileName]
    }

    // https://zh.minecraft.wiki/w/%E5%91%BD%E4%BB%A4/replaceitem
    command("replaceitem") {
        // replaceitem block <position: x y z> slot.container <slotId: int> <itemName: Item> [amount: int] [data: int] [components: json]
        WithSize(10, strict = true) then {
            Positions(10 to ArgSelection.WithInfo(JsonStr)) then {
                Matches("replaceitem block (json)") { cmd, arg ->
                    cmd[1].content == "block" && arg.isJson()
                }
            }
        }
        // replaceitem block <position: x y z> slot.container <slotId: int> <oldItemHandling: ReplaceMode> <itemName: Item> [amount: int] [data: int] [components: json]
        WithSize(11, strict = true) then {
            Positions(11 to ArgSelection.WithInfo(JsonStr)) then {
                Matches("replaceitem block (json)") { cmd, arg ->
                    cmd[1].content == "block" && arg.isJson()
                }
            }
        }
        // replaceitem entity <target: target> <slotType: EntityEquipmentSlot> <slotId: int> <itemName: Item> [amount: int] [data: int] [components: json]
        WithSize(8, strict = true) then {
            Positions(8 to ArgSelection.WithInfo(JsonStr)) then {
                Matches("replaceitem entity (json)") { cmd, arg ->
                    cmd[1].content == "entity" && arg.isJson()
                }
            }
        }
        // replaceitem entity <target: target> <slotType: EntityEquipmentSlot> <slotId: int> <oldItemHandling: ReplaceMode> <itemName: Item> [amount: int] [data: int] [components: json]
        WithSize(9, strict = true) then {
            Positions(9 to ArgSelection.WithInfo(JsonStr)) then {
                Matches("replaceitem entity (json)") { cmd, arg ->
                    cmd[1].content == "entity" && arg.isJson()
                }
            }
        }

        // legacy (Java Edition, until 1.13):
        // https://minecraft.wiki/w/Commands/replaceitem?oldid=1143892
        // replaceitem entity <selector> <slot> <item> [amount] [data] [dataTag]
        // replaceitem block <x> <y> <z> <slot> <item> [amount] [data] [dataTag]
        // `<slot>` is a single argument here (`slot.armor.head`, `slot.container.26`); 1.13 split it
        // into `<slotType> <slotId>`, which is why the entity form has exactly 7 arguments.
        // The `dataTag` argument was dropped in 1.13 and the command itself was replaced by
        // `/item replace` in 1.17 (20w46a): https://minecraft.wiki/w/Commands/replaceitem
        WithSize(7, strict = true) then {
            Positions(-1 to ArgSelection.SnbtEntire) then {
                Matches("legacy replaceitem entity") { cmd, arg ->
                    cmd[1].content == "entity" && arg.mayBeSnbtCompound()
                }
            }
        }
        WithSize(9, strict = true) then {
            Positions(-1 to ArgSelection.SnbtEntire) then {
                Matches("legacy replaceitem block") { cmd, arg ->
                    cmd[1].content == "block" && arg.mayBeSnbtCompound()
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
    command("setblock") {
        // setblock <pos...> <block> [destroy|keep|replace|strict]
        WithSizeIn(4..5) then {
            Positions(4 to ArgSelection.BlockState) then {
                val modes = setOf("destroy", "keep", "replace", "strict")
                Matches("setblock 5th arg check") { cmd, _ ->
                    cmd.args.size == 4 || (cmd.args.size == 5 && (cmd[5].content in modes))
                }
            }
        }

        // setblock <pos...> <block> {snbt}
        // legacy (https://minecraft.wiki/w/Commands/setblock?oldid=1246732)
        // setblock <x> <y> <z> <block> [dataValue|state] [oldBlockHandling] [dataTag]
        WithSizeIn(4..7) then {
            Positions(-1 to ArgSelection.SnbtEntire) then {
                Matches("setblock nbt") { _, arg ->
                    arg.mayBeSnbtCompound()
                }
            }
        }
    }


    // ── data merge (NBT with text components) ────────────────────
    // data merge entity <target> <nbt>
    // data merge storage <source> <nbt>
    command("data") {
        WithSize(4, strict = true) then {
            Positions(4 to ArgSelection.SnbtEntire) then {
                Matches("data merge nbt") { cmd, arg ->
                    cmd[1].content == "merge"
                            && (cmd[2].content == "entity" || cmd[2].content == "storage")
                            && arg.mayBeSnbtCompound()
                }
            }
        }
    }

    // data merge block <pos...> <nbt>
    command("data") {
        WithSize(6, strict = true) then {
            Positions(6 to ArgSelection.SnbtEntire) then {
                Matches("data merge block nbt") { cmd, arg ->
                    cmd[1].content == "merge"
                            && cmd[2].content == "block"
                            && arg.mayBeSnbtCompound()
                }
            }
        }
    }


    // summon <entity> <pos...> [<nbt>]
    command("summon") {
        WithSize(5, strict = true) then {
            Positions(5 to ArgSelection.SnbtEntire).withAry()
        }
    }
}

val BuiltinCommandDataPatterns = mct.pointer.PatternSet {
    dependsOn(BuiltinNbtPatterns)

    +EqualPattern("") // top-level TextComponent

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
