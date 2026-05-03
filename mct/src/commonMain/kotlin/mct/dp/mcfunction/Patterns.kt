package mct.dp.mcfunction

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
    command("tellraw") {
        WithSize(2, strict = true) then {
            +Positions(2)
        }
    }

    // title <targets> <action> <component>  (action != "times")
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
    command("dialog") {
        WithSize(3, strict = true) then {
            Positions(3) then {
                Matches("dialog show") { cmd, _ ->
                    cmd[1].content == "show"
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


    // ── kick (reason may contain translatable text) ──────────────
    // kick <targets> [<reason>]
    command("kick") {
        WithSize(2) then {
            GreedyPositions(2) then {
                Matches("kick reason with text") { _, arg ->
                    arg.content.isTextComponent()
                }
            }
        }
    }


    // ── waypoint (1.21.6+) ───────────────────────────────────────
    // waypoint add <targets> <name> [<displayComponent>]
    command("waypoint") {
        WithSize(4) then {
            Positions(4) then {
                Matches("waypoint add") { cmd, _ ->
                    cmd[1].content == "add"
                }
            }
        }
    }


    // ── execute (recursive: subcommand after `run`) ─────────────
    command("execute") {
        Any() then {
            +GreedyPositions()
        }
    }
}
