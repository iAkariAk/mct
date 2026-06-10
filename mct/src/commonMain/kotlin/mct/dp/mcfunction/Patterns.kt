package mct.dp.mcfunction

import mct.pointer.RegexPattern
import mct.pointer.RightPattern
import mct.text.isTextComponent

val BuiltinMCFPatterns = PatternSet {
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
            +Positions(2)
        }
    }

    // title <targets> (title|subtitle|actionbar) <component>
    // Wiki: /title <targets> (title|subtitle|actionbar) <title>
    // <title> is a raw JSON text component.
    // "times" subcommand has 5 args (fadeIn stay fadeOut) — excluded by Matches.
    // "clear" and "reset" have 2 args — excluded by WithSize(3, strict).
    command("title") {
        WithSize(3, strict = true) then {
            Positions(3) then {
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
            Positions(3 to IndexSelection.SnbtEntire) then {
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
            Positions(3) then {
                Matches("bossbar add displayName") { cmd, _ ->
                    cmd[1].content == "add"
                }
            }
        }
    }

    // bossbar set <id> name <component>
    command("bossbar") {
        WithSize(4) then {
            Positions(4) then {
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
            Positions(5) then {
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
            Positions(6) then {
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
            Positions(6) then {
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
            Positions(7) then {
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
            Positions(4) then {
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
            Positions(4) then {
                Matches("team prefix/suffix") { cmd, _ ->
                    cmd[1].content == "modify" &&
                            (cmd[3].content == "prefix" || cmd[3].content == "suffix")
                }
            }
        }
    }


    // ── data ─────────────────────────────────────────────────────
    // data modify <target> <path> set value <json>
    command("data") {
        WithSize(6, strict = true) then {
            Positions(6) then {
                Matches("data modify value json") { cmd, arg ->
                    cmd[1].content == "modify" &&
                            cmd[4].content == "set" &&
                            cmd[5].content == "value" &&
                            arg.content.isTextComponent()
                }
            }
        }
    }

    // data modify <target> <path> Name set value <json>
    command("data") {
        WithSize(7, strict = true) then {
            Positions(7) then {
                Matches("data modify Name value json") { cmd, arg ->
                    cmd[1].content == "modify" &&
                            cmd[3].content == "Name" &&
                            cmd[5].content == "set" &&
                            cmd[6].content == "value" &&
                            arg.content.isTextComponent()
                }
            }
        }
    }


    // ── give (item with text components in NBT) ─────────────────
    command("give") {
        WithSize(3) then {
            Positions(2) then {
                Matches("give item with text component") { _, arg ->
                    arg.content.contains("\"text\"") ||
                            arg.content.contains("'text'") ||
                            arg.content.contains("item_name") ||
                            arg.content.contains("custom_name")
                }
            }
        }
    }


    // ── item ─────────────────────────────────────────────────────
    // item modify (block|entity) <target> <slot> <modifier>
    command("item") {
        WithSize(5) then {
            Positions(5) then {
                Matches("item modifier (json)") { cmd, arg ->
                    cmd[1].content == "modify" && (
                            arg.content.isTextComponent() || arg.content.contains("{")
                            )
                }
            }
        }
    }

    // item replace (block|entity) <target> <slot> with <item> [count]
    command("item") {
        WithSize(6) then {
            Positions(6) then {
                Matches("item stack component") { cmd, arg ->
                    cmd[1].content == "replace" &&
                            cmd[5].content == "with" &&
                            arg.content.isTextComponent()
                }
            }
        }
    }

    // item replace * <targets> <slot> from * <sourceTarget> <sourceSlot> [<modifier>]
    command("item") {
        WithSize(9) then {
            Positions(9) then {
                Matches("item replace from modifier") { cmd, arg ->
                    cmd[1].content == "replace" &&
                            cmd[5].content == "from" &&
                            arg.content.isTextComponent()
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
            Positions(3) then {
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
            Positions(5 to IndexSelection.SnbtEntire) then {
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
            Positions(4 to IndexSelection.SnbtEntire) then {
                Matches("data merge nbt") { _, arg ->
                    arg.content.startsWith("{")
                }
            }
        }
    }

    // data merge block <pos> <nbt>
    command("data") {
        And(WithSize(6), Regex("merge block")) then {
            Positions(6 to IndexSelection.SnbtEntire) then {
                Matches("data merge block nbt") { _, arg ->
                    arg.content.startsWith("{")
                }
            }
        }
    }


    // summon <entity> <pos> [<nbt>]
    command("summon") {
        WithSize(5, strict = true) then {
            Positions(5 to IndexSelection.SnbtEntire).withAry()
        }
    }
}


val BuiltinMCFunctionDataPatterns = mct.pointer.PatternSet {
    // ── Display entity text ──────────────────────────────────────
    // Matches `>#text` (single text display compound) and `>#text>0` etc. (array elements)
    // Also matches nested text leaves within array element compounds: `>#text>0>#text`
    +RegexPattern("""^>#text(?:>\d+(?:>#(?:text|translate))?)?$""")

    // ── CustomName ───────────────────────────────────────────────
    // CustomName text components in NBT (entities, block entities, etc.)
    +RightPattern(">#CustomName")
    +RegexPattern("""^>#CustomName>#(?:text|translate|fallback)$""")

    // ── Dialog SNBT fields ───────────────────────────────────────
    // /dialog show <targets> {type:"...",title:{...},...}
    // title and external_title are text components
    +RegexPattern("""^>#(?:title|external_title)(?:>\d+(?:>#(?:text|translate))?)?$""")
    // button labels and tooltips
    +RegexPattern("""^>#(?:yes|no|exit_action)>#(?:label|tooltip)(?:>#(?:text|translate))?$""")
    // action buttons
    +RegexPattern("""^>#actions>\d+>#(?:label|tooltip)(?:>#(?:text|translate))?$""")
    // body contents (plain_message)
    +RegexPattern("""^>#body>\d+>#contents(?:>#(?:text|translate))?$""")
    // dialogs list in dialog_list type
    +RegexPattern("""^>#dialogs>\d+>#(?:title|external_title)(?:>\d+(?:>#(?:text|translate))?)?$""")
    // input control labels
    +RegexPattern("""^>#inputs>\d+>#label(?:>#(?:text|translate))?$""")
    // item description in body items
    +RegexPattern("""^>#body>\d+>#description(?:>\d+(?:>#(?:text|translate))?)?$""")
}
