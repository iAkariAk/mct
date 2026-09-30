---
name: mct
description: >
  用 MCT 汉化 Minecraft 地图：抽取文本、翻译、校对、回填成品世界, 并用补丁分发译文；
  当用户想要去翻译或本地化一张Minecraft地图, 或提到MCT 汉化 / 地图翻译 / mct project,
  或说某些文本没有被翻译(漏翻), 或要求去写或补充MCT Pattern时使用
---

## 参考文件 (按需读, 不要一次全读）

| 何时读                                                    | 文件                                                                                                                                                              |
|-----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 任何涉及 CLI 命令、`mct.toml`、缓存文件、路径或构建的问题 | `references/workflow.md`                                                                                                                                          |
| 翻译或校对环节(必读）                                     | `references/translation.md`                                                                                                                                       |
| 用户启用了 `handle_gradient`, 或要要求处理渐变色文本      | `references/gradient.md`                                                                                                                                          |
| 用户报告漏翻、需要写或改 pattern                          | **先读 `references/pattern-selection.md`(路由表：文本在哪 → 写哪层）**, 再按需读 `references/data_pointer.md`、`references/mcfunction.md`、`references/mcjson.md` |

## 核心事实

- 通过给`mct`的大部分传递`-V`将把一切日志打印出来, 也可以选择传递`-lInfo -lDebug -lWarning -lError`中的一个或多个命令查看特定日志
- 下文除了`mct project init`外的`mct project`系列子命令都需要在项目目录调用, 或传递`-D`参数
- **`mappings.json` 是 `{源串: 译串}`, 而源串是纯文本、结构化文本 (Json/Snbt）、Minecraft命令等 **(如 `"\"八千代\""`、
  `["\"Some texts\"",...]`）. 键与值要保持同一套编码, 只替换其中的可见文字. **编码写错时没有任何报错**, 只会静默产出 0
  组替换
- 译文是 **统一**的：同一源串在整张地图只有一个译法
- 值写 `null` 表示 **保留原文**(该条已处理, 不再进 `missing.json`）；空串 `""` 才是「替换为空」
- 逐条翻译与校对由你 (agent）亲自完成；`mct project translate` 让 MCT 自己调 AI/Api 翻译
- 用户报告漏翻之前, **不要**去主动扫描成品世界找漏译

## 流程

### 1. 确定运行前提

#### 探测 CLI：

```bash
mct --version
```

输出形如 `0.0-SNAPSHOT built on 2026-09-30T06:19:29.084Z`(版本 + 构建时间戳）, 可用来确认手上的 jar 是新的.

不可用就问用户别名或 jar 路径, 都没有才从[Github仓库](https://github.com/iAkariAk/mct)的从Action最新可用CI的artifacts下载
`mct-cli`并解压

再确定 **目标语言**与 **语言风格**, 然后 **用一行说明**选定的工作模式, **再询问用户选择下列的哪一种方式**：

- **agent 翻译**(默认）：你亲自写 `mappings.json`
- **API 翻译**：`mct project translate` 批量翻, 你负责校对

只有用户明确要求「用消耗 token 少的方法」, 或直接指定 MTLX 时, 才用 MTLX 替代 `mappings.json`.

#### 询问语言风格

**动手翻译前必须问用户想要什么语言风格. **

- 用户给了风格 → 直接采用, 写进 `mct.toml` 的 `[ai].literature_style`(多行字符串）
- 用户说「你定」「随便」「看着办」 → 走下面的推断, **把推断结论与依据一并说明**, 让用户有机会否决
- 用户只说了目标语言、没提风格 → 仍要问一次. 默认值 (简洁自然的轻小说风格）只适合日式叙事地图, 不要默默套用

#### 用户让你定风格时, 怎么推断

按顺序收集信号, 结论要能用一句话说清「依据是什么」：

1. **地图自身的元信息**：`level.dat` 里的地图名可以用下面的命令读出来.

```bash
mct kit convert -i <地图目录>/level.dat -if nbt -c gzip -of snbt -o level.snbt
```

若用户给了发布页时, 抓取信息把名称、简介、作者写进 `[map_info]`；这三个字段会作为上下文进提示词.

2. **原文本身**：抽取结果就是最好的样本. 读 `missing.json`(或 `cache/all_texts.json`）里的句子
   看它的语气、题材与体裁：是日常对话、史诗旁白、谜题提示, 还是操作说明
3. **结构线索**：成书条目、对话 (dialog）、告示牌数量多, 说明以叙事与角色互动为主；成就与物品名多, 说明偏游戏机制说明
4. **术语与文化背景**：原文里出现专名、宗教或文学引用时, 风格要与之相称 (`translation.md` 里有对应规则）

把推断写成 `literature_style` 的条目, 而不是含糊的一两个形容词. 例如：

```toml
[ai]
literature_style = """
- 使用简洁自然的语言, 轻小说风格. 
- 保持原文的情感色彩和语气. 
- 不要过度意译, 忠实于原文含义. 
- 人名、地名使用目标语言中通行、自然且符合世界观的译名. 
"""
```

`literature_style` 会进入翻译与术语抽取两处提示词, 所以它同时也约束术语译名的风格.

完成判据：探测到CLI、明确了地图路径、项目目录、目标语言、 **语言风格**、翻译模式.

### 2. 初始化项目

使用如下命令初始化项目, 缺省`-D`参数表示在当前目录下初始化, 加上`-D`则在指定目录下初始化:

```bash
mct project init <项目名> --from <地图目录> [--translation-engine=(ai|api)] [-D <初始化目录>]
```

注意:

- 项目包含`mappings.json`, `terms.json`等, 应该 **可持久化维护**, 不要在临时目录或私有工作区`init`项目
- 项目目录也 **不要放在被翻译的地图内部**, 否则复制会递归

### 3. 抽取原文

执行命令:

```bash
mct project update
```

完成之后, 读输出里的三处抽取计数 (region / datapack / cext）, 以及 `Missing N items` 或 `No new items found`.

然后读 `<项目>/missing.json` 作为待翻译清单. 注意集合内的字符串元素不一定是裸文本.

完成判据：拿到了当前批次的未译清单 (或确认没有新项）.

### 4. 可选：术语与地图上下文

- **只有用户要求 100% 遵循 Minecraft 官方译名时**, 才使用`mct kit official download`和`mct kit official combine`
  下载并生成官方术语表`terms.json`. 多数地图用不到原版命名, 这些术语本来也在模型语料里, 不要替用户做这个决定
- 作者、工具、库/包名应该按照原文写入术语表
- 已有 `terms.json` 就直接沿用； **手动翻译时产生的术语必须写进它**. 写法与判定见`references/workflow.md` 第 4.3 节
- **图像等资源**：贴图替换之类的改动走预处理 (`references/workflow.md` 第七节）, 只能用于图像等资源, 绝不能用来改 MCT
  会抽取/回填文本的 `mca` / `json` / `mcfunction`

### 5. 翻译

**agent 翻译 (默认）**：按 `references/translation.md` 的规则, 并遵从此前确定的语言风格, 逐条把 `missing.json` 的项写进
`mappings.json`.

- 从 `missing.json` **复制键**, 再就地替换其中的可见文字
- 遵守译文的编码同构要求 (外层引号、转义、数组结构保持不变）
- **判定不该翻译的条目, 把值写成 `null`**(玩家名、纯机器标识, 以及按规则应保留原文的内容）. 写了 `null`
  就表示 **保留原文**：该键算已处理, 不会再出现在 `missing.json`, `build` 也不会为它生成替换. 不要留空不管, 也不要写成空串
  `""`, 那会把原文替换为空
- 遇到歧义 (同一源串在不同位置该不该有不同译法、某个词是人名还是标识符）时, 按 `translation.md`
  的判定规则判断；若规则确实无法定夺、且差异会影响玩家观感时, **停下来问用户**, 不要臆造第二种译法
- 翻译完成后把术语取最小完整语义核心写入`terms.json`

**API 翻译**：先按 `references/workflow.md` 第四节配置好链路 (`ai` 需要 base URL / 模型 / token；`api` 走本地
MTranServer, 不消耗 token 但没有术语能力）. 配置完成后通过`mct project translate`开始翻译. , 随后进入第 6 步做校对.

判断是否还有缺失项: 调用`mct project check`, 并读取输出

完成判据: `check`输出`All texts were translated; no texts miss`.

### 6. 校对

按 `references/translation.md` 的检查清单逐条过一遍 `mappings.json`. 做法如下：

- 逐条读 `mappings.json`(量小, 可整读）. 可疑条目要判断上下文时, 用 `rg -F '<源串>' <项目>/cache/*_extractions.json`
  反查它出现在哪些结构里 (`pointer` 字段给出位置）
- 重点抓「 **不该翻的翻了**」：选择器非 name 字段、`@name=AAAA` 这类玩家名与目标、资源 ID、UUID、命令关键字、控制码.
- 同时抓「该翻的没翻」、结构被改坏、术语前后不一致、占位符绑定错误
- **单条译法纠错**直接改 `mappings.json`； **术语级**纠错要同时写回 `terms.json`(键取最小完整语义核心）.

完成判据：`translation.md` 检查清单逐项通过.

### 7. 构建与验证

```bash
mct project build -lError -Warning
```

- 如果输出里面存在warning或error那么提醒用户, 让用户决策是否忽略
- 检查输出里的 `Generated N ... replacement groups`. **N 为 0 或明显偏小, 就说明键没匹配上**；此时它照样打印
  `Build complete`, 但成品世界是未翻译的
- `build/` 每次整体重建, 你的手工改动要落在 `src/` 或 `mappings.json`, 并且如果改动了`src/`, 则需要重新调用
  `mct project update`

### 8. 用户想要分享发布地图翻译的处理方法

先询问用户直接分发成品翻译地图是否存在版权纠纷问题

若不存在则按照如下流程打包:

- 创建一个临时目录, 把 `build/` 复制里面并且重命名成地图名
- 若地图带图像等资源改动 (见 `references/workflow.md` 第七节），确认 `preprocessing/` 已配好并跑过
  `mct project preprocessing`, 这些改动已落在 `build/` 里
- 把`mappings.json`, `terms.json`写入到该临时目录 (不要写入到重命名后的地图文件内, 而是与其同级)
- 从skill目录读取`attachment/publish-info.md`, 替换掉其中的占位符 (`&&xx&&`包裹), 然后写入临时目录`README.md`
  (不要写入到重命名后的地图文件内)
- 把临时目录打包成zip压缩包给用户交差
- 清理临时目录

若存在则通过补丁的形式分发:

- 首先调用`mct project patch`来生成补丁文件 (将放置在`<项目目录>/<mct.toml指定的补丁名称>.mctp`
  ）。图像等资源预处理会被一并打进补丁，对方应用时自动生效
- 然后从skill目录读取`attachment/patch-guidance.md`, 替换掉其中的占位符 (`&&xx&&`包裹, 该文件含补丁应用说明),
  把这两个文件打包成zip压缩包给用户交差

*注意*: `attachment/`目录下的`.md`文件的内容如果以`!`开头, 则说明你需要根据这段内容在写入时替换成生成结果.
不要将两个`.md`弄混

### 9. 用户要求时的两个分支

**漏翻**(用户说「这里没被翻译」「漏翻了 xxx」时才触发）：

1. 定位文本:

先用`mct datapack extract` / `mct region extract` 配合 `--disable-filter-*` 关掉该层的过滤, 把全量文本导出来诊断.
如果能在导出结果里找到缺失项, 就按它记录的 `DataPointer` 走下方流程补 `pattern`.

若通过上述步骤没有发现译文, 则：

- 确定数据包: 如果`src/datapacks`是通过压缩包方式存储的, 解压到临时目录再调用`rg`搜索; 若是以文件夹形式存储, 则直接使用
  `rg`. 之后对搜索结果进行分析, 尤其针对`mcfunction`做处理
- 再确定区块: 使用`mct kit export-snbt`把区块文件转成可读的snbt后使用`rg`检索分析
- 把诊断信息总结, 建议用户在<https://github.com/iAkariAk/mct>上反馈

2. **按 `references/pattern-selection.md` 的路由表选层**：先看文本在哪种文件里, 再看它在文件里是什么形态, 然后才去读对应参考写
   pattern. 不要凭「看起来像」直接写.
    - 数据包 JSON → `--pattern-mcjson-pattern`(`references/mcjson.md`）
    - region / NBT (告示牌、`CustomName`、成书、物品名…）→ `--pattern-nbt-pattern`(`references/data_pointer.md`）
    - 命令：命令结构 → `--pattern-command`；物品组件 → `--pattern-command-component`；参数内 SNBT →
      `--pattern-command-data`；不规则语法 → `--pattern-command-regex`(统一见 `references/mcfunction.md`）
    - MCT 默认不扫的文件 → `--pattern-cext`(格式见路由表）
3. pattern 写进项目, 并在 `mct.toml` 的 `[patterns]` 里对应层下引用 (`has_builtin = false` 时才只用自己的文件）.
4. `mct test pointer` / `mct test command` 验证命中, 再重跑 `update` → 翻译 → `build`.

**查源码 / 查内置 pattern 覆盖**(用户明确要求时才做）：

拉取 <https://github.com/iAkariAk/mct> 分析代码, 用来回答「某个文本为什么没被抽出来」「内置 pattern 覆盖了哪些路径」
「这是不是MCT 的 bug」这类问题. 本地已有源码时优先读本地 (`mct/src/commonMain/kotlin/mct/`）；
内置集清单也可以直接用`mct test pattern -c` 打印, 比读源码更快.

## 何时停下来问用户

只有信息确实无法从仓库、CLI 或地图数据中取得时才问, 而且要问得具体：

- CLI 别名/路径探测不到, 且本仓库无法构建
- 目标语言、 **语言风格**、地图路径、项目目录未给定 (语言风格必须问, 见第 1 步）
- 同一源串的歧义处理会明显影响玩家观感, 而规则无法定夺
- 是否要 100% 遵循官方译名 (这决定要不要下载合并语言文件）
- 地图体量很大时, 是否接受整份复制 (`init` 与 `build` 各复制一次）

其他情况一律先自己查证再动手. 
