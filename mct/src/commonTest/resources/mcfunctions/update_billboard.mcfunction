# From the Roguefire


execute if score info.page event.data matches 12.. run scoreboard players set info.page event.data 1
execute if score info.page event.data matches ..0 run scoreboard players set info.page event.data 11
kill @e[type=text_display,tag=info]
kill @e[type=item_display,tag=info]
kill @e[type=block_display,tag=info]

data merge entity @e[type=text_display,limit=1,sort=nearest,tag=info.page.number] {Rotation:[45F,0F],Tags:["info.page.number"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.4f,0.4f,0.4f]},text:[{"bold":false,"score":{"name":"info.page","objective":"event.data"},"underlined":false},"/11"],background:1677721600}



#######################Page 1

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 1 run summon item_display ~ ~2.25 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[6f,6f,6f]},item:{id:"minecraft:diamond_sword",count:1,components:{"minecraft:item_model":"giga:map_logo"}}}

#$execute positioned $(x) $(y) $(z) if score info.page event.data matches 1 run summon text_display ~ ~2.5 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2.5f,2.5f,2.5f]},text:{"bold":true,"color":"$(map_color)","italic":false,"text":"$(map_series)\n$(map_name)","underlined":false},background:1342177280}

#$execute positioned $(x) $(y) $(z) if score info.page event.data matches 1 run summon text_display ~ ~2 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1f,1f,1f]},text:{"bold":false,"color":"#FFFFFF","italic":false,"text":"by: The CTM Community","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 1 run summon text_display ~ ~1.25 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1f,1f,1f]},text:{"bold":false,"color":"#FFF896","italic":false,"text":"Compiled by: KVT & Mowse","underlined":true},background:1342177280}

################Page 2

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 2 run summon text_display ~ ~2.5 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"CTM Dungeons","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 2 run summon text_display ~ ~1 ~ {line_width:400,Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]},text:[{"bold":false,"color":"#FFFFFF","italic":false,"text":"In typical CTM Dungeons, you will ordinarily\n","underlined":false},"be faced with ",{"color":"dark_aqua","text":"Monster Spawners,"},{"color":"#D1903B","text":"\nLoot Chests, "},{"color":"#6FD1C1","text":"& Environmental Obstacles"},{"color":"white","text":"\n\n This time around, "},{"color":"red","text":"crafting has been disabled"}],background:1342177280}

#######################Page 3

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 3 run summon text_display ~ ~2.5 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Your Objective","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 3 run summon text_display ~ ~0.5 ~ {line_width:400,Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]},text:[{"bold":false,"color":"#FFFFFF","italic":false,"text":"Collect the ","underlined":false},{"color":"#7DD8FF","text":"Card Packs"},{"color":"white","text":" at the end of each dungeon and\nreturn them back to your spawn to receive them"},{"text":"\n\nEarn "},{"color":"yellow","text":"Coins "},{"color":"white","text":"in dungeons to buy shop items.\nImprove your "},{"bold":false,"color":"green","text":"Rank "},"to increase your ",{"color":"green","text":"Deck Size"}],background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 3 run summon item_display ~ ~2 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]},item:{id:"minecraft:flint_and_steel",count:1,components:{"minecraft:item_model":"giga:pack"}}}

#######################Page 4

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon text_display ~ ~2.5 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Obtaining Coins","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon text_display ~ ~1 ~ {line_width:400,Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]},text:[{"bold":false,"color":"#FFFFFF","italic":false,"text":"Spawner Gems & Spawner Shards\n will be your primary source of coins","underlined":false},{"text":"\n\nThey can be obtained in various ways"}],background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~1 ~2 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.4f,0.4f,0.4f]},item:{id:"minecraft:echo_shard",count:1,components:{"minecraft:item_model":"giga:spawner_shard"}}}
$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~-1 ~2 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.4f,0.4f,0.4f]},item:{id:"minecraft:heart_of_the_sea",count:1,components:{"minecraft:item_model":"giga:spawner_gem"}}}
$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon text_display ~1 ~2.2 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.3f,0.3f,0.3f]},text:"(+1 Coin)",background:1342177280}
$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon text_display ~-1 ~2.2 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.3f,0.3f,0.3f]},text:"(+10 Coins)",background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~3 ~0.5 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},item:{id:"minecraft:diamond_ore",Count:1b,tag:{CustomModelData:1}}}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~2 ~0.5 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},item:{id:"minecraft:glow_item_frame",Count:1b,tag:{CustomModelData:1}}}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~1 ~0.5 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},item:{id:"minecraft:chest",Count:1b,tag:{CustomModelData:1}}}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~ ~0.5 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},item:{id:"minecraft:decorated_pot",Count:1b,tag:{CustomModelData:1}}}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~-1 ~0.5 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},item:{id:"minecraft:barrel",Count:1b,tag:{CustomModelData:1}}}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~-2 ~0.5 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},item:{id:"minecraft:spawner",Count:1b,tag:{CustomModelData:1}}}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 4 run summon item_display ~-3 ~0.5 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},item:{id:"minecraft:iron_sword",Count:1b,tag:{CustomModelData:1}}}

############Page 5

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 5 run summon text_display ~ ~2.5 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"About Time","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 5 run summon text_display ~ ~0.5 ~ {line_width:400,Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]},text:[{"bold":false,"color":"#FFFFFF","italic":false,"text":"Each dungeon has a set","underlined":false},{"color":"red","text":" time limit"},{"color":"light_purple","text":"\n\nMagic Clocks"},{"color":"white","text":" can be found to add more time"},{"text":"\n\nRunning out of time stops you"},{"color":"red","text":"\nfrom earning more coins"},{"text":"\n\nItems obtained after running\nout of time are"},{"color":"red","text":" cleared"}],background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 5 run summon item_display ~3.85 ~1.65 ~ {Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.4f,0.4f,0.4f]},item:{id:"minecraft:clock",count:1,components:{"minecraft:custom_model_data":{strings:["magic_clock"]}}}}


################Page 6

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 6 run summon text_display ~ ~3.0 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Bonuses","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 6 run summon text_display ~ ~0.5 ~ {line_width:400,alignment:"left",Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]},text:[{"color":"white","text":"In each dungeon, you have the opportunity to\nto earn bonus coins","underlined":false},{"color":"red","text":"\n\n🖤 ","underlined":false},{"color":"white","text":"Survivor: Complete a dungeon without dying"},{"color":"gold","text":"\n\n⛏ "},{"color":"white","text":"Spawner Streak: Mine 4+ spawners in a row\n(Bonus doubles if 10+ spawners)\n\n"},{"color":"white","sprite":"cards:item/loyalty"},{"color":"white","text":" Cards: By playing cards, you can earn\nvarying bonuses "}],background:1342177280}




################Page 7

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 7 run summon text_display ~ ~2.75 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Cards","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 7 run summon text_display ~ ~0.5 ~ {line_width:400,Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.6f,0.6f,0.6f]},text:[{"bold":false,"color":"#FFFFFF","italic":false,"text":"After completing a dungeon\nyou will receive a card pack.\n\nOpen these card packs in the\nlobby to receive special cards.\n\nPlace these cards in your ","underlined":false},{"color":"light_purple","text":"Ender Chest"},{"color":"white","text":"\nto load them in your deck\n\nYou may also sell cards to Jeffery\n\nEarn Coins to increase your Deck Size"}],background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 7 run summon item_display ~-2.5 ~2 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.5f,1.5f,1.5f]},item:{id:"minecraft:flint_and_steel",count:1,components:{"minecraft:item_model":"cards:split"}}}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 7 run summon item_display ~2.5 ~2 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[1.0f,1.0f,1.0f]},item:{id:"minecraft:ender_chest",count:1}}



###################Page 8

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 8 run summon text_display ~ ~2.5 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Novelties","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 8 run summon text_display ~ ~0.5 ~ {line_width:400,Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]},text:[{"color":"white","text":"Some blocks have "},{"color":"green","text":"Cleaner Drops"},{"color":"white","text":" such as Coal and Hay Bales\n\nOres can be mined with any pickaxe and drop "},{"color":"yellow","text":"Coins"},{"color":"white","text":"\n\nKilling mobs will heal you half a heart and give "},{"color":"yellow","text":"Coins"},{"text":"\nThese mobs get stronger over time, but slowly give more coins\n\nArrows "},{"color":"dark_aqua","text":"ignore invulnerablity frames"},{"color":"white","text":", but are easier to dodge"}],background:1342177280}

################### Page 9

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 9 run summon text_display ~ ~2.5 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Novelties II","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 9 run summon text_display ~ ~0.35 ~ {line_width:400,Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]},text:[{"bold":false,"color":"#FFFFFF","italic":false,"text":"On death, you ","underlined":false},{"color":"green","text":"keep items"},{"color":"white","text":", but "},{"color":"red","text":"drop objectives"},{"color":"white","text":"\n\nEvery 50th mob kill gives "},{"color":"light_purple","text":"powerful healing!"},{"color":"white","text":"\n\nYou can also find powerful consumables"},{"bold":true,"color":"#C7ABFF","text":"\n(Giga Items)"},{"text":"\n\nYour attacks have increased reach and\nbypass invulnerability frames, but must\nbe fully off cooldown"}],background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 9 run summon item_display ~2.25 ~2 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.25f,0.25f,0.25f]},item:{id:"minecraft:bundle",Count:1b,tag:{CustomModelData:1,Items:[{id:"minecraft:bow",Count:1b}]}}}
$execute positioned $(x) $(y) $(z) if score info.page event.data matches 9 run summon item_display ~2.25 ~1.65 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.25f,0.25f,0.25f]},item:{id:"minecraft:golden_apple",Count:1b,tag:{CustomModelData:1}}}
$execute positioned $(x) $(y) $(z) if score info.page event.data matches 9 run summon item_display ~2.25 ~1.3 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.25f,0.25f,0.25f]},item:{id:"minecraft:nautilus_shell",count:1,components:{"minecraft:item_model":"giga:star"}}}
$execute positioned $(x) $(y) $(z) if score info.page event.data matches 9 run summon item_display ~2.25 ~.65 ~ {Rotation:[180F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.25f,0.25f,0.25f]},item:{id:"minecraft:diamond_sword",count:1}}

################### Page 10

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 10 run summon text_display ~ ~2.6 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Multiplayer","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 10 run summon text_display ~ ~0.4 ~ {line_width:400,Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]},text:[{"color":"light_purple","text":"Magic Clocks:","underlined":true},{"color":"white","text":" 15s → 5s","underlined":false},{"color":"green","text":"\n\nTeleportation:","underlined":true},{"color":"white","text":" Using the mini-monument will warp you to\nthe furthest player of 16+ blocks","underlined":false},{"color":"red","text":"\n\nRank Quota:"},{"color":"white","text":" The coins required to rankup your\ndeck size will increase exponentially","underlined":false},{"color":"#7AD3FF","text":"\n\nCard Packs:"},{"color":"white","italic":false,"text":" Each player receives a card pack,\nbut each card pack gives less cards","underlined":false}],background:1342177280}

################### Page 11

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 11 run summon text_display ~ ~3 ~ {Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[2f,2f,2f]},text:{"bold":true,"color":"#FFFFFF","italic":false,"text":"Rules","underlined":false},background:1342177280}

$execute positioned $(x) $(y) $(z) if score info.page event.data matches 11 run summon text_display ~ ~0.5 ~ {line_width:400,alignment:"left",Rotation:[0F,0F],Tags:["info"],brightness:{sky:15,block:15},transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.8f,0.8f,0.8f]},text:[{"bold":false,"color":"white","italic":false,"text":"1. Do not use commands","underlined":false},{"text":"\n\n2. Do not leave the overworld"},{"text":"\n\n3. Do not mine blocks in the monument"},{"text":"\n\n4. Do not modify the world's files"},{"text":"\n\n5. Do not make save states of the map\n\n6. Do not play on peaceful mode"}],background:1342177280}



playsound minecraft:ui.button.click master @s ~ ~ ~ 0.15 1 1



