package mct.dp.mcfunction

import mct.text.isTextComponent

val BuiltinPatterns = PatternSet {
    listOf("say", "me", "teammsg").forEach { cmd ->
        // say <message...>
        // me <action...>
        // teammsg <message...>
        command(cmd) {
            Any() then {
                +GreedyPositions()
            }
        }
    }

    listOf("tell", "msg", "w").forEach { cmd ->
        // tell <targets> <message...>
        // msg <targets> <message...>
        // w <targets> <message...>
        command(cmd) {
            WithSize(2) then {
                +GreedyPositions(2)
            }
        }
    }


    // tellraw <targets> <message>
    command("tellraw") {
        WithSize(2) then {
            +Positions(2)
        }
    }

    // title <targets> <action> <component>
    command("title") {
        WithSize(3, strict = true) then {
            Positions(3) then {
                Matches("not times") { cmd, _ ->
                    cmd[2].content != "times"
                }
            }
        }
    }

    // bossbar set <id> name <component>
    command("bossbar") {
        WithSize(4) then {
            Positions(4) then {
                Matches("bossbar name") { cmd, _ ->
                    cmd[1].content == "set" &&
                            cmd[3].content == "name"
                }
            }

        }
    }

    command("scoreboard") {
        // scoreboard objectives add <objective> <criteria> [<displayName>]
        // scoreboard objectives modify <objective> displayname <component>
        WithSize(5, strict = true) then {
            Positions(5) then {
                Matches("objective add/modify displayname") { cmd, _ ->
                    val operator = cmd[2].content
                    cmd[1].content == "objectives"
                            && (operator == "add" || operator == "modify")
                }
            }
        }

        // scoreboard objectives modify <objective> numberformat fixed <component>
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

        // scoreboard players display name <targets> <objective> <text>
        WithSize(6, strict = true) then {
            Positions(6) then {
                Matches("player display name") { cmd, _ ->
                    cmd[1].content == "players" &&
                            cmd[2].content == "display" &&
                            cmd[3].content == "name"
                }
            }
        }

        // scoreboard players display numberformat <targets> <objective> fixed <component>
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

    command("team") {
        // team modify <team> prefix <component>
        // team modify <team> suffix <component>
        WithSize(4, strict = true) then {
            Positions(4) then {
                Matches("team prefix/suffix") { cmd, _ ->
                    cmd[1].content == "modify" &&
                            (cmd[3].content == "prefix" || cmd[3].content == "suffix")
                }
            }
        }
    }



    command("data") {
        // data modify <target> <path> set value <json>
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
        // data modify <target> <path> Name set value <json>
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


    command("give") {
        // give <targets> <item>
        // Only match when item spec contains text components (item_name, custom_name, Name, Lore, etc.)
        WithSize(3) then {
            Positions(2) then {
                Matches("give item with text component") { _, arg ->
                    arg.content.contains(""""text"""") || arg.content.contains("""'text'""")
                }
            }
        }
    }

    command("item") {
        // item modify (block|entity) <target> <slot> <modifier>
        WithSize(5) then {
            Positions(5) then {
                Matches("item modifier (json)") { cmd, arg ->
                    cmd[1].content == "modify" && (
                            arg.content.isTextComponent()
                                    || arg.content.contains("{")
                            )
                }
            }
        }

        // item replace (block|entity) <target> <slot> with <item> [count]
        WithSize(6) then {
            Positions(6) then {
                Matches("item stack component") { cmd, arg ->
                    cmd[1].content == "replace" &&
                            cmd[5].content == "with" &&
                            arg.content.isTextComponent()
                }
            }
        }

        // item replace * <targets> <slot> from * <sourceTarget> <sourceSlot> [<modifier>]
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

}







