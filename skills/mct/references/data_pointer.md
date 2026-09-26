# DataPointer 与 pattern 参考

DataPointer 是 MCT 的**路径寻址系统**。JSON 数据包（`--pattern-mcjson-pattern`）、命令内 SNBT 数据（`--pattern-command-data`）与 region/NBT（`--pattern-nbt-pattern`）三层共用同一套格式与匹配语义。

本文件只讲 pattern 本身；CLI 选项表、内置集的合并与关闭语义见 `workflow.md`。

## 一、匹配模型

「指针」是树中某个值的路径。它被编码成字符串后与 pattern 比对，而判定的唯一依据就是**编码后的字符串**：

| pattern 类型 | 判定 |
|---|---|
| `equal` | 编码串 **等于** `value` |
| `right` | 编码串 **以** `right` 结尾（`endsWith`） |
| `regex` | 正则 **在编码串中命中**（`containsMatchIn`，不是全串匹配） |

三种类型都可带 `negative`（取反）与 `kind`（如何解析命中内容，见第三节）。

候选值会依次与整组 pattern 比对，`matched()` 返回**第一个**命中的 pattern。所以**顺序有意义**：更具体的 pattern 要放在前面。

源码：`mct/src/commonMain/kotlin/mct/pointer/DataPointerPattern.kt`。

## 二、路径编码

| 编码 | 含义 |
|---|---|
| `>#key` | 进入 map/compound 的 `key` |
| `>N` | 进入 list/array 的第 N 个元素 |
| `>` 结尾 | 路径终止于该叶子值 |
| `&>` | key 名中字面量 `>` 的转义 |

示例：

| 编码串 | 指向 |
|---|---|
| `>#display>#Name` | 根 → map `display` → map `Name` |
| `>#components>#lore>0` | 根 → `components` → `lore` → 数组[0] |
| `>#Entities>3>#CustomName` | 根 → `Entities` → 数组[3] → `CustomName` |

三层抽取器产出的编码一致，只是数据来源不同：

| 抽取器 | 数据来源 |
|---|---|
| mcjson | JSON 文件的 JSON 树 |
| region / NBT | `.mca` 区块的 NBT 树 |
| command_data | 命令参数里解析出的 SNBT 树 |

### `right` 的边界语义

`endsWith` 只看尾部，所以更短的 pattern 会同时命中更深的路径。需要锚定时，改用 `regex` 并加上 `$`。

| pattern | 命中 | 不命中 |
|---|---|---|
| `#display>#Name` | `>#display>#Name` | `>#display>#Name>#extra` |
| `#CustomName` | `>#display>#CustomName`、`>#Entities>0>#CustomName` | `>#CustomName>#extra` |
| `#components>#lore` | `>#components>#lore` | `>#components>#lore>0` |
| `#lore>\d+$`（regex） | `>#display>#Lore>0` | `>#lore>abc` |

`regex` 不加 `$` 会在路径**任意位置**命中，这是与 `right` 最容易混淆的地方。

## 三、`kind`：命中之后怎么解析

`kind` 决定「命中内容是什么形态」，也是 pattern 能够递归的原因。

```json
{ "type": "right", "right": ">#Command", "kind": { "type": "command" } }
```

三种取值（`mct/src/commonMain/kotlin/mct/model/patch/Content.kt` 的 `ContentKind`）：

| kind | 含义 |
|---|---|
| 省略（默认 `Text`） | 命中内容整体就是一段文本 |
| `"command"` | 命中内容是一条命令，按**命令抽取**再处理（内置 NBT 的 `>#Command` 用它） |
| `"structure"` | 命中内容是一段结构化数据，按 `format` 重新遍历，并用嵌套 pattern 继续抽取 |

`Structure` 的完整形状：

```json
{
  "type": "structure",
  "format": "snbt_str",
  "patterns": {
    "type": "custom",
    "patterns": [ { "type": "right", "right": ">#text" } ],
    "inherit_from": "nbt"
  }
}
```

`patterns` 是 `DataPointerPatternKind`（`mct/src/commonMain/kotlin/mct/model/ExtensiblePattern.kt`）：

| 取值 | 含义 |
|---|---|
| `{"type": "inherit_from"}` | 直接继承整层（`nbt` / `mcjson` / `command_data`） |
| `{"type": "custom", "patterns": [...]}` | 用给定 pattern 列表 |
| `{"type": "custom", "patterns": null, "inherit_from": "nbt"}` | 继承某层再叠加 |

**注意**：单独写 `{"type": "inherit_from"}` 目前**无法解码**（kotlinx.serialization 会崩）。要继承某一层时，请用 `custom` 形式加 `inherit_from` 字段：

```json
{ "type": "custom", "patterns": null, "inherit_from": "nbt" }
```

`format` 只交给这个遍历器使用，取值 `plain_str` / `snbt_str` / `json_str` / `json_obj` / `nbt_obj`，语义与 `translation.md` 里一致：当片段的 `syntax` 是引号类型时，`format` 描述的是**引号内部**内容的格式。

内置集几乎全部用默认 `Text`；例外是 NBT 的 `>#Command`（`Command`）和战利品表 `set_nbt` 的 `>#functions>\d+>#tag`（`Structure` + `inherit_from: nbt`）。

## 四、内置 NBT / region 集

`BuiltinNbtPatterns`（`mct/src/commonMain/kotlin/mct/nbt/BuiltinPatterns.kt`）先 `dependsOn(ComponentPatterns)`，再加：

| 目标 | pattern |
|---|---|
| 命令方块等的命令 | `Right(">#Command", kind = Command)` |
| 自定义名 | `Right(">#CustomName")` |
| 物品名 / 描述（legacy 与通用） | `Right(">#display>#Name")`、`Right(">#display>#Lore")`、`Right(">#SkullOwner>#Name")`、`Right(">#SkullOwner>#Lore")` |
| 组件内文本：乐器描述、属性修饰符显示名 | `Regex("#instrument>#description$")`、`Regex("#attribute_modifiers>#modifiers>\d+>#display>#value$")` |
| 展示实体 | `Regex("(^\|>#Entities>\d+)>#text$")`、`Regex("(^\|>#Entities>\d+)>#description$")`、`Regex("(^\|>#Entities>\d+)>#raw_text$")` |
| 成书 | `Regex("(^\|>#Book>#tag)>#(title\|author\|pages\|display\|filtered_pages\|filtered_title)$")`、`Regex(">#tag>#(pages\|title\|author)$")` |
| 告示牌（正反面） | `Regex(">#block_entities>\d+>#(front\|back)_text>#(filtered_)?messages(>\d+>#raw)?$")` |
| 命令方块反馈 | `Regex(">#block_entities>\d+>#LastOutput$")` |
| 方块实体描述 | `Regex(">#block_entities>\d+>#description$")` |
| 地图标记旗帜名 | `Right("#banners>#name")` |
| 旧版 tile entity | `Regex("(^\|>#TileEntities>\d+)>#Text\d$")` |

`ComponentPatterns`（`mct/src/commonMain/kotlin/mct/pointer/CommonPatterns.kt`，mcjson 与 NBT 共用）：

```text
>#components>#(minecraft:)?custom_name(>#raw)?$
>#components>#(minecraft:)?item_name(>#raw)?$
>#components>#(minecraft:)?text_display(>#raw)?$
>#components>#(minecraft:)?description(>#raw)?$
>#components>#(minecraft:)?lore(>\d+>#raw)?$
>#components>#(minecraft:)?written_book_content>#(?:pages|title|author)(?:>\d+>#(?:raw|filtered))?$
>#components>#(minecraft:)?writable_book_content>#pages(?:>\d+>#(?:raw|filtered))?$
```

region/NBT 与 mcjson 的差异：

- **文本组件整体命中**：看起来像文本组件的 compound（`{"text":"..."}`）会整体成为一个候选，pattern 匹配的是**容器路径**，而不是内部的单个键。
- **命令字段**：`Command` 路径走命令抽取器，不走 DataPointer。
- **查看实际数据**：`mct kit export-snbt -i <图> -o <目录>` 把区块 NBT 导成可读的 SNBT，再用 `rg` 找文本最快。

## 五、验证

```bash
mct test pattern -c                 # 打印最终组装出的 pattern 集合（内置 + 自定义）
mct test pointer -k mcjson -p my.json '>#display>#Name'
mct test pointer -k region -p my.json '>#block_entities>0>#CustomName'
mct test pointer -k region --no-builtin -p my.json '<指针>'   # 只测自定义，不叠加内置
```

`test pointer` 打印 `true` 或 `false`，是验证单条路径最快的办法。改完 pattern 必须先用它确认，再重跑 `project update`。
