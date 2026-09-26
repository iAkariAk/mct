# MCJson pattern 参考

数据包 `.json` 文件（`--pattern-mcjson-pattern`）的 pattern。抽取流程分三步：

1. **解码**：用宽容配置 `MCJson` 解码，它顺便把 Minecraft 的非标准 JSON（单引号、注释、尾逗号）标准化。
2. **遍历**：从 JSON 树构建 `DataPointer` 路径（对象键 + 数组下标）。
3. **过滤**：候选逐个与 pattern 集比对；**第一个**命中的 pattern 决定命中内容如何解析。

DataPointer 的三种 pattern 类型、`kind` 递归、路径编码见 `data_pointer.md`；CLI 选项与合并语义见 `workflow.md`。

## 一、pattern 文件格式

`List<DataPointerPattern>`：

```json
[
  { "type": "right", "right": "#display>#Name" },
  { "type": "regex", "regex": "#display>#Lore>\\d+$" },
  { "type": "right", "right": "#CustomName" },
  { "type": "regex", "regex": "#functions>\\d+>#name>#(?:text|translate|fallback)$" }
]
```

## 二、JSON 树遍历规则

遍历器（`mct/src/commonMain/kotlin/mct/dp/mcjson/Extract.kt`）对每个节点按下列规则产出候选：

| 节点 | 条件 | 候选 |
|---|---|---|
| 字符串叶子 | always | 该字符串本身，格式由自身内容推断 |
| 数组 | **看起来是**文本组件 | **整个数组**，`FormatKind.JsonObj` |
| 对象 | **看起来是**文本组件 | **整个对象**，`FormatKind.JsonObj` |
| 对象 | 简写形式 `{"":"text"}` | 对象被改写成 `{"text":"text"}` 后整体作为候选，`JsonObj` |
| 其他原始值 | — | 不产出 |

例：

```json
{
  "display": {
    "Name": "{\"text\":\"My Sword\"}",
    "Lore": ["Line 1", "Line 2"]
  },
  "components": { "custom_name": "{\"text\":\"Epic Sword\"}" }
}
```

产出的候选：

| 指针 | 内容 | format |
|---|---|---|
| `>#display>#Name` | `{"text":"My Sword"}` | `json_str` |
| `>#display>#Lore` | `["Line 1","Line 2"]` | `json_obj` |
| `>#components>#custom_name` | `{"text":"Epic Sword"}` | `json_str` |

**`>#display>#Lore>0` 不会被产出**：数组一旦被判定为文本组件列表，就整体作为一个候选，不再下探元素。所以告示牌 `messages`、书本 `pages`、legacy `Lore` 这类列表的 pattern 要指向**容器路径**（`>#pages`、`>#display>#Lore$`），而不是元素路径。

format 列就是切片的 `FormatKind`：容器候选是 `json_obj`，字符串叶子按自身内容推断，序列化的组件是 `json_str`，裸文本是 `plain_str`。

### 判定「看起来是文本组件」

对象：全部键都在文本组件字段表内，且除结构字段（`extra`、`with`、`hover_event`、`click_event`、`score`、`separator`、`player`、`shadow_color` 等）外的值都不是 map/collection。数组：每个元素都是字符串或各自是文本组件。另外 `{"":"text"}` 这种把 `text` 简写成空键的对象会被识别并展开。见 `mct/src/commonMain/kotlin/mct/model/text/Util.kt`。

### 指针编码

| 编码 | 含义 |
|---|---|
| `>#key` | map 取键 |
| `>N` | 数组取下标 N |
| `>` 结尾 | 路径终止于叶子 |
| `&>` | 键名中字面量 `>` 的转义 |

## 三、内置 pattern 目录

`BuiltinMCJsonPatterns`（`mct/src/commonMain/kotlin/mct/dp/mcjson/BuiltinPatterns.kt`）先用 `dependsOn(ComponentPatterns)` 带上共用集，再加自己的。顺序有意义：`matched()` 返回第一个命中的。

### 现代组件（共用 `ComponentPatterns`）

| pattern | 类型 |
|---|---|
| `>#components>#(minecraft:)?custom_name(>#raw)?$` | regex |
| `>#components>#(minecraft:)?item_name(>#raw)?$` | regex |
| `>#components>#(minecraft:)?text_display(>#raw)?$` | regex |
| `>#components>#(minecraft:)?description(>#raw)?$` | regex |
| `>#components>#(minecraft:)?lore(>\d+>#raw)?$` | regex |
| `>#components>#(minecraft:)?written_book_content>#(?:pages\|title\|author)(?:>\d+>#(?:raw\|filtered))?$` | regex |
| `>#components>#(minecraft:)?writable_book_content>#pages(?:>\d+>#(?:raw\|filtered))?$` | regex |

### 成就

| pattern | 类型 |
|---|---|
| `>#display>#title` | right |
| `>#display>#description` | right |

### 旧版物品显示（1.20.5 之前）

| pattern | 类型 |
|---|---|
| `>#display>#Name` | right |
| `>#display>#Lore$` | regex |

### 实体与方块实体

| pattern | 类型 |
|---|---|
| `>#CustomName` | right |

### 告示牌

| pattern | 类型 |
|---|---|
| `>#(front\|back)_text>#messages$` | regex |

### 成书

| pattern | 类型 |
|---|---|
| `>#pages` | right |
| `>#title` | right |
| `>#author` | right |

### 对话

| pattern | 类型 |
|---|---|
| `>#external_title` | right |
| `>#(?:yes\|no\|after_action\|exit_action\|actions>\d+)>#(?:label\|tooltip)$` | regex |

### 战利品表与物品修饰器

| pattern | 类型 |
|---|---|
| `>#functions>\d+>#(name\|lore)$` | regex |
| `>#functions>\d+>#entity>#name$` | regex |
| `>#functions>\d+>#modifiers>\d+>#name$` | regex |
| `>\d+>#lore` | regex |
| `>#functions>\d+>#tag$` | regex，kind = `Structure(snbt_str, inherit_from: nbt)` |

最后一条对应 `minecraft:set_nbt`：命中的字符串会作为 SNBT **重新遍历**，用 NBT pattern 集继续抽里面的文本组件。

### 描述（1.21+ 唱片机曲目、物品描述）

| pattern | 类型 |
|---|---|
| `>#description` | right |

## 四、JSON 标准化

Minecraft 常用单引号 JSON，这不是合法标准 JSON：

```json
{'text':'Hello','color':'red'}
```

`MCJson`（`mct/src/commonMain/kotlin/mct/dp/mcjson/MCJson.kt`）是配置了 `isLenient`、`allowTrailingComma`、`allowIllegalEscape`、`allowSingleQuote` 的 `NonstandardJson`，并且**始终接受注释**。它的 `standardize`（`mct/src/commonMain/kotlin/mct/util/NonstandardJson.kt`）会扫描文本并改写：

- 单引号 `'text'` → 双引号 `"text"`
- 单引号字符串内的转义单引号 `\'` → `'`
- 单引号字符串内嵌套的双引号保留（转义）
- 混合引号风格一并处理
- 引号外的 `//` 与 `/* */` 注释丢弃

改写只作用于引号外的文本，所以字符串里单独的 `/`（URL、日期、路径）保持原样。

解码会自动标准化，因此**写 pattern 时可以用标准 JSON 语法**，即使目标文件用单引号。回填用紧凑的标准 `Json` 配置重新编码，所以输出文件不保留注释与单引号。

## 五、回填

替换组是 `DatapackReplacementGroup(source, path, replacements)`：`source` 是数据包目录/压缩包名，`path` 是包内文件路径。`backfillDatapack` 遍历数据包，按这个 `(source, path)` 精确匹配文件，再按指针应用替换：

- 指针终止于 `JsonObj` 替换 → 替换整个数组/对象候选；
- `JsonStr` / `SnbtStr` / `PlainStr` 终止 → 替换字符串叶子。

## 六、验证

```bash
mct test pointer -k mcjson -p my.json '>#display>#Name'
mct test pointer -k mcjson --no-builtin -p my.json '<指针>'
mct datapack extract -i <图> -o test.json --pattern-mcjson-pattern my.json -V
```
