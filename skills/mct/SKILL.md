---
name: mct
description: >
  用 MCT 汉化 Minecraft 地图与数据包：抽取文本、翻译、校对、回填成品世界，并用补丁分发译文；
  地图里的文本没被抽取出来时补写 pattern。Use when the user wants to translate or
  localize a Minecraft map or datapack, mentions MCT 汉化 / 地图翻译 / mct project,
  says some text was not translated or 漏翻, or asks to write or fix MCT patterns.
---

## 参考文件（按需读，不要一次全读）

| 何时读 | 文件 |
|---|---|
| 任何涉及 CLI 命令、flag、`mct.toml`、缓存文件、路径或构建的问题 | `references/workflow.md` |
| 翻译或校对环节（必读） | `references/translation.md` |
| 用户启用了 `handle_gradient`，或要处理渐变色文本 | `references/gradient.md` |
| 用户报告漏翻、需要写或改 pattern | **先读 `references/pattern-selection.md`（路由表：文本在哪 → 写哪层）**，再按需读 `references/data_pointer.md`、`references/mcfunction.md`、`references/mcjson.md` |

references 可能与当前源码不同步。任何结论都要能被源码或真实 CLI 复现验证；两者冲突时以源码与 CLI 为准。

## 核心事实

- **`mappings.json` 是 `{源串: 译串}`，而源串是 IR 编码串**（如 `"\"八千代\""`、`["\"Some texts\"",...]`）。键与值要保持同一套编码，只替换其中的可见文字。**编码写错时没有任何报错**，只会静默产出 0 组替换。
- 译文是**上下文无关**的：同一源串在整张地图只有一个译法。
- 值写 `null` 表示**保留原文**（该条已处理，不再进 `missing.json`）；空串 `""` 才是「替换为空」。
- 逐条翻译与校对由你（agent）亲自完成是**默认路径**；`mct project translate` 让 MCT 自己调 AI 只是可选加速。
- 用户报告漏翻之前，**不要**去主动扫描成品世界找漏译。
- 不要主动联网抓取仓库或地图信息；只在用户明确要求时做（见第 9 步）。

## 流程

### 1. 确定运行前提

探测 CLI：

```bash
mct --version
```

不可用就问用户别名或 jar 路径；都没有才从仓库构建（`./gradlew :cli:shadowJar` 或 Windows 的 `cmd /c gradlew.bat :cli:shadowJar`，产物 `cli/build/libs/cli-0.0-SNAPSHOT-all.jar`）。确定一种可用形式后全程复用。

再确定**目标语言**与**语言风格**，然后**用一行说明**选定的工作模式，再开始下面的步骤：

- **agent 翻译**（默认）：你亲自写 `mappings.json`。
- **API 翻译**：`mct project translate` 批量翻，你负责校对。

选哪条路取决于约束而非偏好：`mct.toml` 里有没有可用 token、`missing.json` 的规模、用户是否已经说清。只有用户明确要求「用消耗 token 少的方法」，或直接指定 MTLX 时，才用 MTLX 替代 `mappings.json`。

#### 先问语言风格

**动手翻译前必须问用户想要什么语言风格。** 风格是译文的基调，事后返工代价最高，所以这不能替你猜。

- 用户给了风格 → 直接采用，写进 `mct.toml` 的 `[ai].literature_style`（多行字符串）。
- 用户说「你定」「随便」「看着办」 → 走下面的推断，**把推断结论与依据一并说明**，让用户有机会否决。
- 用户只说了目标语言、没提风格 → 仍要问一次。默认值（简洁自然的轻小说风格）只适合日式叙事地图，不要默默套用。

#### 用户让你定风格时，怎么推断

按顺序收集信号，结论要能用一句话说清「依据是什么」：

1. **地图自身的元信息**：`level.dat` 里的地图名可以直接读出来。

   ```bash
   mct kit convert -i <地图目录>/level.dat -if nbt -c gzip -of snbt -o level.snbt
   ```

   （`init` 之后就是 `<项目>/src/level.dat`；`-c gzip` 必须给，它是 gzip 压缩的 NBT。）

   用户给了发布页时，把名称、简介、作者写进 `[map_info]`；这三个字段会作为上下文进提示词。
2. **原文本身**：抽取结果就是最好的样本。读 `missing.json`（或 `cache/all_texts.json`）里的句子，看它的语气、题材与体裁：是日常对话、史诗旁白、谜题提示，还是操作说明。
3. **结构线索**：成书条目、对话（dialog）、告示牌数量多，说明以叙事与角色互动为主；成就与物品名多，说明偏游戏机制说明。
4. **术语与文化背景**：原文里出现专名、宗教或文学引用时，风格要与之相称（`translation.md` 里有对应规则）。

把推断写成 `literature_style` 的条目，而不是含糊的一两个形容词。例如：

```toml
[ai]
literature_style = """
- 使用简洁自然的语言，轻小说风格。
- 保持原文的情感色彩和语气。
- 不要过度意译，忠实于原文含义。
- 人名、地名使用目标语言中通行、自然且符合世界观的译名。
"""
```

`literature_style` 会进入翻译与术语抽取两处提示词，所以它同时也约束术语译名的风格。

完成判据：CLI 形式、地图路径、项目目录、目标语言、**语言风格**、翻译模式六项都已确定并声明。

### 2. 建项目

```bash
mct project init <项目名> --from <地图目录> [--translation-engine=(ai|api)]
```

**项目目录是持久落点，不要放进临时目录或私有工作区**：`mappings.json`、`terms.json` 与翻译成果都住在里面，要能跨会话续跑。默认就建在当前目录下，通常不必传参。

`init` 会把整份地图复制进项目，地图很大时先提醒用户磁盘占用；项目目录也不要放在被翻译的地图内部，否则复制会递归。

### 3. 抽取

```bash
mct project update        # 在项目目录内执行
```

读输出里的三处抽取计数（region / datapack / cext），以及 `Missing N items` 或 `No new items found`。

然后读 `<项目>/missing.json` 作为待翻译清单。注意它是**IR 编码串**的集合，不是裸文本。

**注意**：`missing.json` 只在有新项时被覆写，全部已译时**旧的不会删**。判断「还有没有未译项」看 `update` 的输出，不看文件是否存在。

完成判据：拿到了当前批次的未译清单（或确认没有新项）。

### 4. 可选：术语与地图上下文

- **只有用户要求 100% 遵循 Minecraft 官方译名时**，才走零 token 路径生成官方术语表：`mct kit official download`，再 `mct kit official combine`。多数地图用不到原版命名，这些术语本来也在模型语料里，不要替用户做这个决定。
- 用户给出地图的发布页/来源时，抓取地图信息（名称、简介、作者）写入 `mct.toml` 的 `[map_info]`（推断风格时已用到，见第 1 步）。作者名会被 MCT 保护：不翻译、不提取为术语。
- 已有 `terms.json` 就直接沿用；**手动翻译时产生的术语必须写进它**，否则下一批文本或以后改用 AI 翻译时同一术语会被译成别的。写法与判定见 `references/workflow.md` 第 4.3 节。

### 5. 翻译

**agent 翻译（默认）**：按 `references/translation.md` 的规则，并遵从此前确定的语言风格，逐条把 `missing.json` 的项写进 `mappings.json`。

- 从 `missing.json` **复制键**，再就地替换其中的可见文字；不要手写编码。
- 遵守译文的编码同构要求（外层引号、转义、数组结构保持不变）。
- **判定不该翻译的条目，把值写成 `null`**（玩家名、纯机器标识，以及按规则应保留原文的内容）。写了 `null` 就表示「决定保留原文」：该键算已处理，不会再出现在 `missing.json`，`build` 也不会为它生成替换。不要留空不管，也不要写成空串 `""`，那会把原文替换为空。
- 遇到歧义（同一源串在不同位置该不该有不同译法、某个词是人名还是标识符）时，按 `translation.md` 的判定规则定夺；规则确实无法定夺、且差异会影响玩家观感时，**停下来问用户**，不要臆造第二种译法。

**API 翻译**：先按 `references/workflow.md` 第四节配置好链路（`ai` 需要 base URL / 模型 / token；`api` 走本地 MTranServer，不消耗 token 但没有术语能力），再用最小抽取池验证一次，确认能出译文再对全量跑 `mct project translate`（在项目目录内执行），随后进入第 6 步做校对。

完成判据：`missing.json` 的每一项在 `mappings.json` 里都有非空译文。

### 6. 校对

按 `references/translation.md` 的检查清单逐条过一遍 `mappings.json`。做法如下：

- 逐条读 `mappings.json`（量小，可整读）。可疑条目要判断上下文时，用 `rg -F '<源串>' <项目>/cache/*_extractions.json` 反查它出现在哪些结构里（`pointer` 字段给出位置）。
- 重点抓「**不该翻的翻了**」：选择器非 name 字段、`@name=AAAA` 这类玩家名与目标、资源 ID、UUID、命令关键字、控制码。
- 同时抓「该翻的没翻」、结构被改坏、术语前后不一致、占位符绑定错误。
- **单条译法纠错**直接改 `mappings.json`；**术语级**纠错要同时写回 `terms.json`（`Map<String,String>`，键取最小完整语义核心）。这就是术语持久化：缺了它，下一批文本或改用 AI 翻译时还会跑偏。手动翻译阶段定下的每个专名与固定功能名都要在这里登记。
- **发现未译项不要手写补，重跑 `mct project update`** 让它更新 `missing.json`。

完成判据：`update` 报告 `No new items found`，且 `translation.md` 检查清单逐项通过。

### 7. 构建与验证

```bash
mct project build         # 在项目目录内执行
```

- 检查输出里的 `Generated N ... replacement groups`。**N 为 0 或明显偏小，就说明键没匹配上**；此时它照样打印 `Build complete`，但成品世界是未翻译的。
- 交付前必须验一次真实结果：`mct kit export-snbt -i <项目>/build -o <临时目录>`，再在导出内容里 `rg` 原文特征串，确认译文进了图、原文没有残留。
- `build/` 每次整体重建，手工改动要落在 `src/` 或 `mappings.json`。
- 需要分发给他人时走补丁（见下节），不要让对方拿到你的整个项目目录。

完成判据：成品世界里能检索到译文，且原文已无残留（或残留部分已确认属于应保留的机器数据）。

### 8. 以补丁形式发布（用户要发布或分享译文时才做）

`project build` 只回填**你自己**项目里的 `build/`。要把译文本地交付给别人，生成 `.mctp` 补丁：

```bash
mct project patch                 # 在项目目录内；产物 <项目>/<名称>.mctp
```

对方拿**同一张地图**自行应用：

```bash
mct patch apply -i <地图目录> -p <补丁.mctp>
```

- 补丁内默认带整张地图的 SHA1 哈希树，应用时不匹配会**拒绝**并列出差异，所以对方必须用同一版本的地图；建议提醒对方先备份（补丁是就地修改）。
- `immediate`（默认）把替换组固化在补丁里，最省事；`deferred` 体积小，但要求对方环境能复现抽取。不确定就用默认。
- `-f json`（默认）可读可改；`-f cbor` 体积略小。创建与应用必须用同一格式。
- 只有明确要「不问版本硬套」时才用 `--no-validation`。

细节与三种验证策略见 `references/workflow.md` 第七节。

### 9. 用户要求时的两个分支

**漏翻**（用户说「这里没被翻译」「漏翻了 xxx」时才触发）：

1. 定位文本：在 `src/` 里 `rg`，或 `mct kit export-snbt` 后检索。
2. **按 `references/pattern-selection.md` 的路由表选层**：先看文本在哪种文件里，再看它在文件里是什么形态，然后才去读对应参考写 pattern。不要凭「看起来像」直接写。
   - 数据包 JSON → `--pattern-mcjson-pattern`（`references/mcjson.md`）
   - region / NBT（告示牌、`CustomName`、成书、物品名…）→ `--pattern-nbt-pattern`（`references/data_pointer.md`）
   - 命令：命令结构 → `--pattern-command`；物品组件 → `--pattern-command-component`；参数内 SNBT → `--pattern-command-data`；不规则语法 → `--pattern-command-regex`（统一见 `references/mcfunction.md`）
   - MCT 默认不扫的文件 → `--pattern-cext`（格式见路由表）
3. pattern 写进项目，并在 `mct.toml` 的 `[patterns]` 里对应层下引用（`has_builtin = false` 时才只用自己的文件）。
4. `mct test pointer` / `mct test command` 验证命中，再重跑 `update` → 翻译 → `build`。

**查源码 / 查内置 pattern 覆盖**（用户明确要求时才做，不要主动）：

拉取 <https://github.com/iAkariAk/mct> 分析代码，用来回答「某个文本为什么没被抽出来」「内置 pattern 覆盖了哪些路径」「这是不是 MCT 的 bug」这类问题。本地已有源码时优先读本地（`mct/src/commonMain/kotlin/mct/`）；内置集清单也可以直接用 `mct test pattern -c` 打印，比读源码更快。

## 何时停下来问用户

只有信息确实无法从仓库、CLI 或地图数据中取得时才问，而且要问得具体：

- CLI 别名/路径探测不到，且本仓库无法构建。
- 目标语言、**语言风格**、地图路径、项目目录未给定（语言风格必须问，见第 1 步）。
- 同一源串的歧义处理会明显影响玩家观感，而规则无法定夺。
- 是否要 100% 遵循官方译名（这决定要不要下载合并语言文件）。
- 地图体量很大时，是否接受整份复制（`init` 与 `build` 各复制一次）。

其他情况一律先自己查证再动手。
