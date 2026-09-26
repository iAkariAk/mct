# 命令 pattern 参考

`.mcfunction` 文件、NBT 里的命令字符串（`>#Command`）以及 JSON 里的命令字符串，共用同一套**分层** pattern。四层各有自己的 pattern 类型与 flag：

| 层 | flag | 作用 | pattern 类型 |
|---|---|---|---|
| 1 命令结构 | `--pattern-command` | 按命令名、参数个数筛选，指定抽取哪个参数 | `CommandExtractPattern` |
| 2 物品组件 | `--pattern-command-component` | 过滤 `id[key=value,...]` 组件表 | `ComponentPattern` |
| 3 SNBT 数据 | `--pattern-command-data` | 按 DataPointer 路径过滤 SNBT 参数 | `DataPointerPattern` |
| 4 裸正则 | `--pattern-command-regex` | 绕开解析，扫全文 | `CommandRegexPattern` |

另外还有两个通道**始终生效，无法关闭**：

- **target selector 内在抽取**：`@p[...]`、`@a[...]` 等里的 `name=` 值。
- **递归子命令**：`execute ... run` / `return run` 后面的命令会被重新解析并按普通命令匹配。

处理顺序（`mct/src/commonMain/kotlin/mct/command/Extract.kt`）：

```text
源文本
  ├─ 解析出 List<MCCommand>
  │    └─ 每条命令：target selector 切片 + 命令 pattern 切片
  │         （pre 条件 → 位置选择器 → post 条件 → 参数选择）
  └─ 若配了 commandRegex：对整份源文本跑正则，切片追加在最后
```

CLI 选项表与合并语义见 `workflow.md`；DataPointer 路径规则见 `data_pointer.md`。

## 一、命令结构 pattern（`--pattern-command`）

```text
CommandExtractPattern
  ├── command    命令名（"say"、"give"、"item" …）
  ├── pre        这条命令是否够格
  ├── selector   抽哪个参数、怎么抽
  └── post       对抽到的参数再做一次过滤
```

pattern 按命令名分组（`ExtractPatternSet = Map<String, List<CommandExtractPattern>>`）；同一个命令下的多条 pattern 都会贡献切片。

```json
[
  {
    "command": "say",
    "pre": { "type": "any" },
    "selector": { "type": "greedy", "position": 0 },
    "post": { "type": "any" }
  }
]
```

### PreCondition：命令是否够格

| `type` | 字段 | 行为 |
|---|---|---|
| `any` | — | always |
| `with_size` | `size: Int`、`strict: Boolean = false` | 非 strict：参数**至少** `size` 个（下界）；strict：恰好 `size` 个 |
| `regex` | `regex: String` | 对整行原文 `containsMatchIn` |
| `and` / `or` / `none` | `conditions: List<PreCondition>` | 组合 |

**参数计数按词法 token，不按 wiki 语法槽位**。词法器把 `[...]`、`{...}`、带引号字符串各视为一个参数，所以 `@e[tag=foo]` 算 1 个、`{"text":"hi"}` 算 1 个；但 Minecraft 的贪心「message」类型会按空格拆词，`/tell @a hello world` 是 3 个参数而 wiki 形状 `<targets> <message>` 看起来是 2 个。所以非 strict 是下界：带贪心尾巴的命令用 `with_size` ≤ 最小 token 数来放行，只有不带贪心尾巴的形状才该用 `strict: true`。

```json
{ "type": "with_size", "size": 3, "strict": true }
{ "type": "and", "conditions": [ { "type": "with_size", "size": 4 }, { "type": "regex", "regex": "merge (entity|storage)" } ] }
```

### IndexSelector：抽哪个参数

**参数位置一律 1-based**（`command[1]` 是命令名后的第一个参数）。

#### `greedy`：取一段原文范围

```json
{ "type": "greedy", "position": 0 }
```

| position | 对 `say hello world` | 抽出 | 逻辑 |
|---|---|---|---|
| `0` | 特例 | `hello world` | 从命令名之后开始 |
| `1` | 第一个参数起 | `hello world` | `command[1].relativeIndices.first` |
| `2` | 第二个参数起 | `world` | `command[2].relativeIndices.first` |

greedy 切片一律是 `PlainStr`，且 **post 条件不会作用于 greedy**。

#### `non_greedy`：指定位置

```json
{ "type": "non_greedy", "indexes": { "2": null } }
{ "type": "non_greedy", "indexes": { "3": { "type": "snbt_entire" } } }
```

`indexes` 是「1-based 位置 → `ArgSelection`」；值为 `null` 等价于 `{"type": "plain_entire"}`。post 条件在选择之前生效。

### ArgSelection：抽出来怎么解析

| `type` | 字段 | 结果 |
|---|---|---|
| `plain_entire` | — | 整个参数按纯文本 |
| `text_component_entire` | — | 整个参数按 JSON 或 SNBT 文本组件（`JsonStr` / `SnbtStr`）；不是文本组件则报错并回退为纯文本 |
| `snbt_entire` | — | 参数按 SNBT 解析，遍历文本叶子，用 `--pattern-command-data` 过滤 |
| `with_info` | `format: FormatKind`、`syntax: SnbtSyntaxKind? = null` | 整个参数，强制指定 format/syntax |
| `item_stack` | — | `id[组件表]`：每个组件的文本用 `--pattern-command-component` 过滤；`id{旧NBT}`：走 SNBT 并用 `--pattern-command-data` 过滤；裸 `id`：不抽 |
| `block_state` | — | `id[states]{snbt}`：走 SNBT 并用 `--pattern-command-data` 过滤 |

`FormatKind` 序列化名：`plain_str`、`snbt_str`、`json_str`、`json_obj`、`nbt_obj`。
`SnbtSyntaxKind` 取值（无自定义名）：`Compound`、`List`、`SingleQuoteString`、`DoubleQuoteString`、`LiteralString`。

**`syntax` 与 `format` 是两个轴**：`syntax` 是切片的**外层词法形态**；`format` 是「引号内部内容」的格式（当 `syntax` 是引号类型时），否则是切片内容自身的格式。`inferFormatKind(syntax = …)` 会先脱引号再探测内容，所以一个内容是 JSON 的 SNBT 引号字符串是 `json_str` 而不是 `plain_str`。

各选择器产出的 format：

- greedy 与 `plain_entire` → `plain_str`。
- `snbt_entire`：文本组件 compound/list → `snbt_str`；其中的字符串叶子 → `inferFormatKind(syntax)`。
- `text_component_entire`：JSON 组件 → `json_str`；SNBT 组件 → `snbt_str`。
- `with_info`：按声明的 `format`/`syntax`。
- target selector 的 `name=` 切片与正则切片：未显式声明时用 `inferFormatKind(syntax)`。

选择失败时（SNBT 解析失败、要求文本组件但不是）MCT 记录 `Selection fails: ...`，并回退为**整个参数按纯文本**。

### PostCondition：对抽到的参数再过滤

| `type` | 字段 | 行为 |
|---|---|---|
| `any` | — | always |
| `regex` | `regex: String` | 对 `arg.content` `containsMatchIn` |
| `contain` | `content: String` | 子串包含 |
| `equal` | `content: String` | 完全相等 |
| `at` | `position: Int`、`condition: PostCondition` | 委托给 `command[position]`（1-based） |
| `and` / `or` / `none` | `conditions: List<PostCondition>` | 组合 |

内置集里还用了 **DSL 专有**的 `Matches { cmd, arg -> ... }` 谓词。它是编译期 Kotlin，**无法用 JSON 表达**；自定义 pattern 只能用 `regex` / `contain` / `equal` / `at` 近似。

### 完整示例

```json
[
  { "command": "say", "pre": { "type": "any" },
    "selector": { "type": "greedy", "position": 0 }, "post": { "type": "any" } },

  { "command": "tell", "pre": { "type": "with_size", "size": 2 },
    "selector": { "type": "greedy", "position": 2 }, "post": { "type": "any" } },

  { "command": "tellraw", "pre": { "type": "with_size", "size": 2, "strict": true },
    "selector": { "type": "non_greedy", "indexes": { "2": { "type": "text_component_entire" } } },
    "post": { "type": "any" } },

  { "command": "title", "pre": { "type": "with_size", "size": 3, "strict": true },
    "selector": { "type": "non_greedy", "indexes": { "3": { "type": "text_component_entire" } } },
    "post": { "type": "at", "position": 2,
              "condition": { "type": "none", "conditions": [ { "type": "equal", "content": "times" } ] } } },

  { "command": "give", "pre": { "type": "with_size", "size": 2 },
    "selector": { "type": "non_greedy", "indexes": { "2": { "type": "item_stack" } } },
    "post": { "type": "any" } },

  { "command": "data",
    "pre": { "type": "and", "conditions": [ { "type": "with_size", "size": 4 },
                                             { "type": "regex", "regex": "merge (entity|storage)" } ] },
    "selector": { "type": "non_greedy", "indexes": { "4": { "type": "snbt_entire" } } },
    "post": { "type": "regex", "regex": "^\\{" } }
]
```

### 内置命令目录

`BuiltinCommandPatterns`（`mct/src/commonMain/kotlin/mct/command/BuiltinPatterns.kt`）。**实测规模：20 个命令键 / 48 条 pattern**（`mct test pattern -c` 统计），其中 `item` 占 17 条。下表 `WithSize(n)` 未标 strict 即为下界语义。

| 命令 | pre | selector | post |
|---|---|---|---|
| `say`、`me`、`teammsg` | any | greedy 0 | any |
| `tell`、`msg`、`w` | with_size 2 | greedy 2 | any |
| `tellraw` | with_size 2 strict | `{2: text_component_entire}` | any |
| `title` | with_size 3 strict | `{3: text_component_entire}` | `cmd[2] != "times"` |
| `dialog` | with_size 3 strict | `{3: snbt_entire}` | `cmd[1] == "show"` 且参数以 `{` 开头 |
| `bossbar` add | with_size 3 strict | `{3: text_component_entire}` | `cmd[1] == "add"` |
| `bossbar` set name | with_size 4 | `{4: text_component_entire}` | `cmd[1] == "set"` 且 `cmd[3] == "name"` |
| `scoreboard` objectives add/modify displayname | with_size 5 strict | `{5: text_component_entire}` | `cmd[1] == "objectives"` 且（`cmd[2] == "add"` 或 (`cmd[2] == "modify"` 且 `cmd[4] == "displayname"`)） |
| `scoreboard` objectives modify numberformat fixed | with_size 6 strict | `{6: text_component_entire}` | `cmd[1] == "objectives"`、`cmd[2] == "modify"`、`cmd[4] == "numberformat"`、`cmd[5] == "fixed"` |
| `scoreboard` players display name | with_size 6 strict | `{6: text_component_entire}` | `cmd[1] == "players"`、`cmd[2] == "display"`、`cmd[3] == "name"` |
| `scoreboard` players display numberformat fixed | with_size 7 strict | `{7: text_component_entire}` | `cmd[1] == "players"`、`cmd[2] == "display"`、`cmd[3] == "numberformat"`、`cmd[6] == "fixed"` |
| `team` add | with_size 3 strict | `{3: text_component_entire}` | `cmd[1] == "add"` |
| `team` modify displayName | with_size 4 strict | `{4: text_component_entire}` | `cmd[1] == "modify"` 且 `cmd[3] == "displayName"` |
| `team` modify prefix/suffix | with_size 4 strict | `{4: text_component_entire}` | `cmd[1] == "modify"` 且 `cmd[3]` 为 `prefix`/`suffix` |
| `data` modify entity/storage … set value | with_size 7 strict | `{7: text_component_entire}` | `cmd[1] == "modify"`、`cmd[2]` 为 `entity`/`storage`、`cmd[5] == "set"`、`cmd[6] == "value"` |
| `data` modify block … set value | with_size 9 strict | `{9: text_component_entire}` | `cmd[1] == "modify"`、`cmd[2] == "block"`、`cmd[7] == "set"`、`cmd[8] == "value"` |
| `data` merge entity/storage | `and(with_size 4, regex "merge (entity\|storage)")` | `{4: snbt_entire}` | 参数以 `{` 开头 |
| `data` merge block | `and(with_size 6, regex "merge block")` | `{6: snbt_entire}` | 参数以 `{` 开头 |
| `give` | with_size 2 | `{2: item_stack}` | any |
| `setblock` | with_size 5 | `{5: snbt_entire}` | 参数以 `{` 开头 |
| `summon` | with_size 5 strict | `{5: snbt_entire}` | any |
| `kick` | with_size 2 | greedy 2 | any |
| `replaceitem` block | with_size 10 strict | `{10: with_info(JsonStr)}` | `cmd[1] == "block"` 且参数是 JSON |
| `replaceitem` block + replace mode | with_size 11 strict | `{11: with_info(JsonStr)}` | 同上 |
| `replaceitem` entity | with_size 8 strict | `{8: with_info(JsonStr)}` | `cmd[1] == "entity"` 且参数是 JSON |
| `replaceitem` entity + replace mode | with_size 9 strict | `{9: with_info(JsonStr)}` | 同上 |
| `item`（17 条） | 见下 | 见下 | 见下 |

`item` 的 17 条遵循三种形状（post 还要求 modifier 参数不是命名空间 id；当物品写成 `with <item>` 时改用 `item_stack`）：

| 形状 | 位置 | 选择器 |
|---|---|---|
| `item modify entity/block <target> <path> <modifier>` | 5 / 7（strict） | `snbt_entire` |
| `item replace/fill/override … from entity/block <modifier>` | 同源 9 / 13（strict）；跨源 entity↔block 11（strict） | `snbt_entire` |
| `item replace/fill/override … with <item>` | entity 6 / block 8 | `item_stack` |

包装子命令的命令（`execute run`、`return run`）由递归处理，不需要 pattern。另有几条命令看起来会带文本、实际不会，包括 `spreadplayers`、`waypoint`、`damage`、`kill`、`fill`、`place`；具体情况以 <https://minecraft.wiki/w/Commands> 为准。

## 二、物品组件 pattern（`--pattern-command-component`）

被 `ArgSelection.item_stack` 使用，对应现代 `id[key=value,...]` 写法。组件键用 `findByCompoundKey` 查找：`namespace:name`，或不带命名空间的 `name`（默认 `minecraft`）。

```json
[
  { "namespace": "minecraft", "name": "custom_name" },
  { "namespace": "minecraft", "name": "lore", "pattern": { "type": "right", "right": ">#text" } }
]
```

- 省略/为 null 的 `pattern`：该组件只有在产出**恰好一个**文本切片时才被抽。
- 有 `pattern`：只保留指针命中该 pattern 的切片。

内置集 `BuiltinMinecraftComponentPatterns`：

| `name` | `pattern` |
|---|---|
| `custom_name` | null |
| `item_name` | null |
| `text_display` | null |
| `description` | null |
| `lore` | null |
| `written_book_content` | regex `>#(?:text\|author\|pages)$` |
| `writable_book_content` | right `pages` |

`--disable-builtin-command-component` 必须同时给 `--pattern-command-component`，否则 panic。

## 三、SNBT 数据 pattern（`--pattern-command-data`）

参数被按 SNBT 解析时生效（`snbt_entire`、`item_stack` 的 `id{旧NBT}` 形式、`block_state`）。匹配用与 mcjson/NBT 相同的 DataPointer 格式，详见 `data_pointer.md`。

```json
[
  { "type": "equal", "value": "" },
  { "type": "right", "right": "#display>#Name" },
  { "type": "regex", "regex": "#components>#minecraft:custom_name$" },
  { "type": "right", "right": "#CustomName", "negative": true }
]
```

`BuiltinCommandDataPatterns` 先 `dependsOn(BuiltinNbtPatterns)`，再加：

| pattern | 类型 |
|---|---|
| `""`（顶层文本组件） | equal |
| `>#name` | equal |
| `>#text` | right |
| `>#CustomName` | right |
| `^>#(?:title\|external_title)$` | regex |
| `>#(?:yes\|no\|after_action\|exit_action\|actions>\d+)>#(?:label\|tooltip)$` | regex |
| `^>#body>\d+>#contents$` | regex |
| `^>#dialogs>\d+>#(?:title\|external_title)$` | regex |
| `^>#inputs>\d+>#label$` | regex |
| `^>#body>\d+>#description$` | regex |

格式上要注意：文本组件的 compound 或 list 回来是 `snbt_str`；单个 SNBT 字符串叶子则用 `inferFormatKind(syntax)` 推断，内容是 JSON 的引号叶子是 `json_str`，裸字面量是 `plain_str`。

## 四、裸正则 pattern（`--pattern-command-regex`）

绕开命令解析，直接扫整份源文本，用于命令层够不到的写法。这一层没有内置集，也没有 `--disable-*` 开关；你给的文件就是全集。

```json
[
  {
    "regex": "minecraft:custom_name=('[^']+'|\\{[^}]+\\})",
    "groups": {
      "0": null,
      "1": { "syntax": "SingleQuoteString" }
    }
  }
]
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `regex` | String | 正则源；每条 pattern 对全文 `findAll` |
| `groups` | `Map<Int, GroupInfo?>` | 捕获组下标 → 信息；`0` 是整个匹配。值为 `null` 表示按匹配文本推断 syntax/format |

`GroupInfo`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `syntax` | `SnbtSyntaxKind?` | `Compound`、`List`、`SingleQuoteString`、`DoubleQuoteString`、`LiteralString` |
| `format` | `FormatKind?` | `plain_str`、`snbt_str`、`json_str`、`json_obj`、`nbt_obj`。可空；省略时（或整个 `GroupInfo` 为 `null`）从匹配文本推断，`syntax` 是引号类型时先脱引号 |

正则切片**追加**在命令 pattern 切片之后，且不与它们去重。正则集没产出时，只返回命令 pattern 切片。

## 五、递归子命令

`execute ... run <command>` 与 `return run <command>` 会被展开：第一个 `run` 参数之后的内容重建为一条新命令，按普通命令匹配。嵌套链迭代展开。

```text
execute as @p run tellraw @a {"text":"Hello"}
  ↓ 重建 "tellraw @a {"text":"Hello"}"
  → tellraw pattern 抽出位置 2 的组件
```

不需要为 `execute` / `return` 写 pattern。

## 六、target selector 内在抽取

在 pattern 匹配之前，先扫描 target selector 里的 `name=`：

```text
@p[name=foo]                  → "foo"          （LiteralString）
@p[name="hello world"]        → "hello world"  （DoubleQuoteString）
@p[name='hello']              → "hello"        （SingleQuoteString）
@p[name=!exclude_me]          → "exclude_me"   （LiteralString）
@e[type=player,name="foo"]    → "foo"          （DoubleQuoteString）
```

由 `CommandExtractorIntrinsic`（`mct/src/commonMain/kotlin/mct/command/Extract.kt`）实现，**始终生效、无法用 pattern 关闭**。与命令 pattern 切片重叠的内在切片会被丢弃。

注意这一通道只抓 `name=` 字段。`@name=AAAA` 这类写法属于选择器 name 字段的值，本身也是玩家名；校对时不要把它当普通文本误翻（见 `translation.md`）。

## 七、验证

```bash
mct test command -i test.mcfunction --pattern-command my.json   # 高亮命中范围
mct test pattern -c                                             # 打印最终 pattern 集合
mct datapack extract -i <图> -o test.json --pattern-command my.json -V
```
