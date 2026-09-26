# 该写哪种 pattern：路由表

漏翻时先定位文本，再按**文本所在的文件**与**它在文件里的形态**选层。

选错层是这类问题最常见的失败方式：把 JSON 指针写到命令层，或把命令 pattern 写到 DataPointer 层，都不会报错，只是什么都抽不出来。

各层详细规则见 `data_pointer.md`（路径匹配）、`mcfunction.md`（命令四层）、`mcjson.md`（JSON 遍历）。

## 第一步：文本在哪种文件里

| 文本所在文件 | 谁扫它 | 用哪层 pattern |
|---|---|---|
| 数据包 `.json` | `datapack` / `project` | `--pattern-mcjson-pattern` |
| `.mcfunction` | `datapack` / `project` | 命令四层（见第二步） |
| 数据包里的 `.nbt` | `datapack` | `--pattern-nbt-pattern` |
| 地图 `region/*.mca`、`entities/*.mca`、`poi/*.mca` | `region` / `project` | `--pattern-nbt-pattern`；若该文本是一条命令（如命令方块的 `Command` 字段），走命令层 |
| `level.dat`、`data/*.dat`、自定义配置等 MCT 默认不扫的文件 | `cext` / `project` | `--pattern-cext` |

不同文件的抽取器出口不同：`.json` 走 MCJson 遍历器，`.mcfunction` 走命令抽取，`.mca` 与 `.nbt` 走 NBT 遍历器。**它们共用同一套 DataPointer 路径编码**，所以同一段文本在不同文件里可能需要不同的 flag，但指针写法一致。

## 第二步：文本在文件里是什么形态

| 形态 | 层 | pattern 文件格式 | 最小示例 |
|---|---|---|---|
| JSON 对象的某个字符串值 / 整个文本组件数组或对象 | mcjson | `List<DataPointerPattern>` | `[{"type":"right","right":"#display>#Name"}]` |
| NBT 里的文本组件（告示牌、CustomName、成书、物品名…） | nbt | `List<DataPointerPattern>` | `[{"type":"regex","regex":">#block_entities>\\d+>#(front\|back)_text>#messages$"}]` |
| 命令本身要抽参数（`say`、`tellraw`、`title`、`give`…） | command | `List<CommandExtractPattern>` | `[{"command":"say","pre":{"type":"any"},"selector":{"type":"greedy","position":0},"post":{"type":"any"}}]` |
| 命令里的物品组件 `id[custom_name=…]` | command_component | `List<ComponentPattern>` | `[{"namespace":"minecraft","name":"custom_name"}]` |
| 命令参数里的 SNBT 数据（`data merge entity …`、`summon … {…}`） | command_data | `List<DataPointerPattern>` | `[{"type":"right","right":"#CustomName"}]` |
| 命令里语法不规则的文本（拿不准结构、命令解析够不到） | command_regex | `List<CommandRegexPattern>` | `[{"regex":"name=('[^']+')","groups":{"1":{"syntax":"SingleQuoteString"}}}]` |
| MCT 默认不扫的文件 | cext | **单个 `CextPattern` 对象**（不是数组） | 见下节 |

**各层的文件格式不一样，不要混用**：DataPointer 层是 pattern 数组，`command` / `command_component` / `command_regex` 也是数组但元素类型各不相同，而 `--pattern-cext` 是**一个对象**。

## 第三步：cext 的写法

`cext` 用 `select` 挑文件（针对**相对地图根目录**的路径做整串匹配，第一个命中的生效），再用 `kind` 决定怎么解析。

```json
{
  "customs": [
    {
      "select": ".*\\.mcfunction",
      "kind": { "type": "mcfunction" }
    },
    {
      "select": "myconfig\\.json",
      "kind": { "type": "mcjson" }
    }
  ]
}
```

带内置预设时（`opt_in` 的元素是**对象**，不是字符串；写成 `["level_dat"]` 会解码失败）：

```json
{
  "opt_in": [{ "type": "level_dat" }],
  "customs": []
}
```

| `kind.type` | 含义 | 关键字段 |
|---|---|---|
| `mcjson` | 按 JSON 解析 | `patterns`（默认继承 mcjson 层） |
| `mcfunction` | 按命令解析 | `command`、`commandData`、`commandRegex`（默认分别继承对应层） |
| `nbt` | 按二进制 NBT 解析 | `compression`（`none`/`gzip`/`zlib`）、`patterns`（默认继承 nbt 层） |
| `snbt` | 按 SNBT 文本解析 | `patterns`（默认继承 nbt 层） |

`opt_in` 是可选的**内置 cext 预设**列表，元素写成 `{"type": "<预设名>"}`。目前只有一个预设 `level_dat`（匹配 `level\\.dat`，gzip NBT，抽取 `>#>#Data>#LevelName`）。只写 `customs` 也可以，仓库里的 `example/cext.json` 就是纯 `customs` 的形式。

三个 `kind` 的 `patterns` 默认都继承同名层，所以写 `{"type":"mcjson"}` 就够了；要改就用 `{"type":"custom","patterns":[…],"inherit_from":"mcjson"}` 这种形式（单独的 `{"type":"inherit_from"}` 无法解码）。

## 第四步：验证

```bash
# DataPointer 层
mct test pointer -k mcjson -p my.json '>#display>#Name'
mct test pointer -k region -p my.json '>#block_entities>0>#CustomName'

# 命令层（高亮命中范围）
mct test command -i test.mcfunction --pattern-command my.json

# 看最终组装出的全部 pattern（内置 + 自定义）
mct test pattern -c

# 全量抽取复核
mct datapack extract -i <图> -o out.json --pattern-mcjson-pattern my.json --pattern-nbt-pattern my.json -V
```

改完重跑 `project update`，再翻译、`build`。如果用 `mct.toml` 管理 pattern，就把配置写在 `[patterns]` 对应的层下（只有 `has_builtin = false` 时才只用你自己的文件）。

## 常见误判

| 症状 | 真正的问题 |
|---|---|
| 指针写对了却抽不出来 | 层选错了：JSON 文件的路径拿去 `--pattern-nbt-pattern`，或反过来 |
| 数组里只有第一行被抽出 | 文本组件数组要匹配**容器路径**（`>#pages`），不是 `>#pages>0` |
| 命令里的文本抽不出来 | 命令层没配：只写了 `--pattern-mcjson-pattern`，但文本在 `.mcfunction` 里 |
| `>#Command` 里的文本抽不出来 | 它是命令字符串，要命令层 pattern，不是 DataPointer |
| 文件里明明有文本，`rg` 也搜得到 | 该文件在 MCT 的默认扫描范围外，要用 cext |
| 用 `--disable-builtin-*` 后什么都不抽了 | 没同时给 pattern 路径（`command`、`command_component` 层会直接 panic） |
