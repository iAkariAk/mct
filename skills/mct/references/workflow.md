# MCT 工作流与 CLI 参考

本文是 `mct` CLI 与 `mct project` 汉化流程的权威参考。

请以源码与真实 CLI 输出为准：文中每一条都可以复现验证。若本文与 `mct <命令> --help` 冲突，以 CLI 为准。

相关文件：翻译与校对规则见 `translation.md`；pattern 怎么写见 `pattern-selection.md`（先选层）与 `data_pointer.md`、`mcfunction.md`、`mcjson.md`。

## 一、调用 CLI

优先使用用户已经配好的 `mct`（在 PATH 上或是个 shell 别名）。先探测：

```bash
mct --version
```

探测不到就问用户别名或 jar 路径。两者都没有，才从本仓库构建：

```bash
./gradlew :cli:shadowJar          # Windows: cmd /c gradlew.bat :cli:shadowJar
java -jar cli/build/libs/cli-0.0-SNAPSHOT-all.jar --version
```

若构建因 Gradle 缓存目录的权限失败，不要改项目文件，重跑同一条命令即可。确定可用形式后，全程复用。

`java -jar` 会打印 jna 的 `WARNING: ... restricted method ...`，这是正常噪声，用 `2>/dev/null` 过滤即可。

构建产物是整包 fat jar，启动较慢（约 1–3 秒），批处理时不要在循环里反复启动。

## 二、`mct project` 全流程

`project` 面向「同一个地图反复翻译、反复重建」的场景：它把抽取、术语、翻译、回填串成一组可以断点续跑的步骤。

```text
project init <名称> --from <地图目录> [-D <父目录，可选>] [--translation-engine=(ai|api)]
project update        抽取，产出缓存与 missing.json
project term          （可选）用 AI 从 missing.json 抽术语写回 terms.json
project translate     （可选）用 AI 翻译，产出 mappings.json
project build         生成成品世界（src → build 副本 + 回填）
project patch         生成 .mctp 补丁文件
```

### 项目目录布局

```text
<projectDir>/
  mct.toml                     配置
  src/                         地图副本（init 时整份复制，注意磁盘占用）
  mappings.json                {源串: 译串或 null}，源串为 IR 编码串；null＝决定保留原文
  terms.json                   {原文术语: 译名}
  missing.json                 update 产出的未译池（**可能陈旧**，见常见问题 1）
  cache/
    all_texts.json             全量文本池（Set<String>）
    region_extractions.json    region 抽取组
    datapack_extractions.json  datapack 抽取组
    cext_extractions.json      cext 抽取组
    build_mappings.json        build 时合并 MTLX 后的最终映射
    region_replacements.json   生成的 region 替换组
    datapack_replacements.json
    cext_replacements.json
  build/                       成品世界（build 每次整体重建）
  <patch.name>.mctp            project patch 的产物
```

`-D/--project-dir` 是所有 `project` 子命令的公共选项，**默认就是当前目录**，而且**不是必需的**。它的含义随子命令而变，这是最容易搞错的一点：

| 子命令 | `-D` 指向 |
|---|---|
| `init` | **父目录**；`init` 用项目名在它下面创建项目目录 |
| 其余（`update` / `term` / `translate` / `build` / `patch`） | **项目目录本身**（即含 `mct.toml` 的那一层） |

最省事的用法是 **`cd` 进项目目录后直接跑，省略 `-D`**。除 `init` 外，子命令都在项目目录里找 `mct.toml`，找不到就 panic「Not a project directory」；把父目录误传给它，会得到「Source directory is not a valid Minecraft world: <父目录>/src」。

### 各步骤的输入输出

| 步骤 | 读 | 写 |
|---|---|---|
| `init` | 源地图目录 | `mct.toml`、`src/` |
| `update` | `src/`、`mct.toml`、已存在的 `mappings.json` | 三份抽取缓存、`cache/all_texts.json`、`missing.json`（条件性） |
| `term` | `missing.json`、已存在的 `terms.json` | `terms.json` |
| `translate` | 三份抽取缓存、`mappings.json`、`terms.json` | `mappings.json`、`terms.json` |
| `build` | `mappings.json`、三份抽取缓存、`mct.toml`（含 `mtlx`） | `build/`、`cache/*_replacements.json`、`cache/build_mappings.json` |
| `patch` | `mappings.json`、三份抽取缓存 | `<name>.mctp` |

`update` 并发跑 region / datapack / cext 三路抽取，并覆写全部缓存。`translate`、`build`、`patch` 会先检查缓存是否存在，没有就 panic「No extractions found in cache. Run `project update` first.」。

### `--translation-engine` 与 `[translation.engine]`

`init --translation-engine=(ai|api)` 决定 `mct.toml` 里 `[translation.engine].type`：

- `ai`：`project translate` 与 `project term` 走 `[ai]` 的 OpenAI 兼容接口，需要有效 token。
- `api`：走 `[translation.engine.value]` 配置的本地翻译服务（默认 MTranServer `http://127.0.0.1:8989/`），不需要 token。

`api` 引擎没有术语能力，而且 `project term` 仍然要求 token。用 `api` 引擎时，术语只能靠 `mct kit official` 或自己维护术语表。

## 三、`mct.toml` 字段

`project init` 生成的配置（实测）只写入**非默认值、非 null 的字段**。因此看不到 `version`、`description`、`mtlx`、`extra_prompts` 是正常的：它们默认 null，需要时手动加。

```toml
name = "demo"
version = "..."            # 可选
description = "..."        # 可选
mappings = "mappings.json"
mtlx = "..."               # 可选；设了才启用 MTLX
terms = "terms.json"
pretty_json = true

[patterns]                 # 见下节；每层 { patterns = [...], has_builtin = true }
nbt = { patterns = [], has_builtin = true }
mcjson = { patterns = [], has_builtin = true }
command = { patterns = [], has_builtin = true }
command_data = { patterns = [], has_builtin = true }
command_component = { patterns = [], has_builtin = true }
command_regex = []
cext = []

[map_info]                 # 供 LLM 理解上下文
name = "..."               # 可选
description = "..."        # 可选
authors = []               # 非空时：作者名不翻译、也不提取为术语

[ai]
api_url = "https://api.openai.com/v1/"
token = "@ENV_VAR"         # @前缀引用环境变量
model = "gpt-4o"
use_stream_api = true
token_threshold = 2048
literature_style = """..."""
target_language = "简体中文"
static_check = false       # 只影响 CLI 内置提示词的输出前自检
temperature = 1.0
handle_gradient = false
http_logging = false
thinking_output = false

[translation]
concurrency = 1            # 并发会削弱术语效果，默认 1
concurrent_by_kind = false

[translation.engine]
type = "ai"
[translation.engine.value] # type 非 ai 时在此填 api 引擎配置

[patch]
name = "..."               # 可选，默认跟随项目名
kind = "immediate"         # immediate | deferred
```

### `[patterns]` 每层的 `patterns` 与 `has_builtin`

- `has_builtin = true`（默认）：内置集在**前**，配置的文件追加在**后**。
- `has_builtin = false`：只用配置的文件，等价于 CLI 的 `--disable-builtin-<层>`。

这与 CLI 的默认方向一致（都是 builtin + custom）。区别在于 `has_builtin = false` 时配置文件**替掉**内置集，所以「内置 + 自定义」和「只用自定义」两种意图都能表达，也不会出现 CLI 那种「没传 pattern 路径却传了 `--disable-builtin` 就 panic」的组合。

`command` 与 `command_component` 两层的模式是 `List`/`Set`，`cext` 也是列表且多个文件会**合并**（`optIn` 与 `customs` 各自拼接）。

## 四、配置翻译引擎（AI 与 API）

`project translate` 与 `project term` 走哪条链路，由 `mct.toml` 的 `[translation.engine].type` 决定。两条链路的配置方式完全不同：先问清用户手里有什么，再动手配。

| 链路 | 需要 token | 术语能力 | 适用 |
|---|---|---|---|
| `ai`（OpenAI 兼容接口） | 是 | 有（`project term` + `terms.json`） | 用户有可用 API；术语一致性重要 |
| `api`（本地翻译服务） | 否 | **无** | 用户没有 token；量大；要离线、免费或本地可控 |
| 不配（agent 自翻） | 否 | 靠 `terms.json` 手动维护 | **默认路径**，见 `SKILL.md` 第 1 步 |

### 4.1 配置 `ai` 链路

```toml
[ai]
api_url = "https://api.openai.com/v1/"   # 任何 OpenAI 兼容端点，以 /v1/ 结尾；如 https://api.deepseek.com/v1/
token = "@MY_API_KEY"                    # @前缀引用环境变量，避免把密钥写进文件
model = "gpt-4o"
use_stream_api = true                    # 部分供应商会返回空响应，开启更稳
target_language = "简体中文"
temperature = 1.0
token_threshold = 2048                   # 单次请求的最大 token
static_check = false                     # 内置提示词的输出前自检
thinking_output = false                  # 打印模型思考过程（占终端）
handle_gradient = false                  # 启用后按 references/gradient.md 处理渐变文本
http_logging = false                     # 调试用
literature_style = """..."""              # 风格要求，会进翻译与术语抽取两处提示词；先问用户，用户让你定时再按地图信息推断（见 SKILL.md 第 1 步）
extra_prompts = "..."                    # 追加到所有提示词末尾；填错会明显影响质量
```

配置步骤：

1. 向用户要齐 **base URL、模型名、token** 三项；不要猜测或编造模型名。
2. token 优先写成 `@环境变量名`，让密钥留在环境里。用户坚持明文写进文件也可以。
3. 让用户确认模型是否支持流式；不确定就保持 `use_stream_api = true`。
4. 先用最小代价验证：对很小的抽取池跑一次 `project translate`，确认能出译文且没有 401/404，再对全量跑。

token 未配置（空或仍是模板默认值）时，`project translate` / `project term` 会直接 panic「AI token not configured. Set [ai.token] in mct.toml」。

### 4.2 配置 `api` 链路（MTranServer）

这是本地翻译服务，源码与部署方式见 <https://github.com/xxnuo/MTranServer>（Docker 或直接运行二进制）。它默认监听 `8989`；可用环境变量改端口与访问控制：`MT_PORT` 改端口、`MT_API_TOKEN` 设访问令牌、`MT_OFFLINE` 限制模型下载。

`project init --translation-engine=api` 会生成这样一段（实测）：

```toml
[translation.engine]
    type = "MTranServer"

    [translation.engine.value]
        api_url = "http://127.0.0.1:8989/"
        # token = "..."                      # 服务端设了 MT_API_TOKEN 时才需填
        config = { max_retry = 20, target = "zh_cn" }
```

| 字段 | 说明 |
|---|---|
| `type` | `"MTranServer"`。这是 `[translation.engine.value]` 的**判别名**，不是 `init --translation-engine` 那个 `api` 值 |
| `api_url` | 服务地址，末尾带 `/` |
| `token` | 可选。填了会带 `Authorization: Bearer <token>`；服务端设了 `MT_API_TOKEN` 就必须填 |
| `config.max_retry` | 失败重试次数（默认 20） |
| `config.source` | 源语言；留空即请求里的 `from: auto` |
| `config.target` | 目标语言，默认 `zh_cn` |

对接细节（`extra/src/commonMain/kotlin/mct/extra/ai/translator/ApiTranslator.kt`）：MCT 向 `$api_url/translate/batch` POST `{"from": <source 或 "auto">, "to": <target>, "texts": [...], "html": false}`，再从响应的 `results` 数组取值。

**语言代码对不上，是这类配置最常见的失败**：MCT 默认写 `target = "zh_cn"`，而 MTranServer 的文档用 `zh-Hans` 这类写法。先 `curl $api_url/languages` 看服务实际接受什么，再把 `target` 改成服务认的代码。

验证与排障：

```bash
curl http://127.0.0.1:8989/health          # 服务是否活着
curl http://127.0.0.1:8989/languages       # 支持的语言代码
```

连不上时依次确认四件事：服务是否已启动；端口是否被 `MT_PORT` 改过；`api_url` 末尾有没有 `/`；`MT_API_TOKEN` 与 toml 里的 `token` 是否一致。

**术语限制**：`api` 链路的 `ApiTranslator.terms` 是一张未使用的空表，术语一致性完全靠手工维护 `terms.json`；`project term` 也仍然要求 AI token，用 `api` 链路时不要指望它。

### 4.3 `terms.json`：手动翻译时的术语持久化

`terms.json` 是 `Map<String, String>`（原文 → 译名，与 `mappings.json` 同构），也是**跨批次、跨引擎共享的术语真相**：

- 作为 `project translate` / `project term` 的 `defaultTerms` 输入，提示词里要求 LLM 优先采用；
- `project translate` 结束后会把本轮新增术语回写进来。

所以**手动翻译时遇到稳定术语，要写进 `terms.json`，而不是只写进 `mappings.json`**。只写 mapping 的话，该术语就只是一条普通译文：等到下一批文本里再出现同一个词，或者日后改用 `project translate`，它会被译成别的，全文一致性随之断掉。

写法：直接编辑 `<项目>/terms.json`：

```json
{
  "Red Cross Knight": "红十字骑士",
  "Shimamura": "岛村"
}
```

- 键取原文的**最小完整语义核心**：不含外围装饰符号、引号与列表标记，也不是整句；`名称：描述` 只取冒号前的名称。判定规则见 `translation.md`。
- 值只译该核心，不加装饰。
- 已经翻好、当时却漏记的术语，在校对阶段补记回来（见 `SKILL.md` 第 6 步）。
- 文件名由 `[translation].terms` 决定（默认 `terms.json`）；改了这项配置，要同步改文件名。

## 五、抽取 / 回填命令组

除 `project` 之外，还有几组直接面向地图的命令，适合单次操作或调试：

| 命令组 | 作用 |
|---|---|
| `mct datapack extract|backfill` | 数据包（`.json`、`.mcfunction`、`.nbt`） |
| `mct region extract|backfill` | `.mca` 区域文件 |
| `mct cext extract|backfill` | 按路径正则自定义抽取 |
| `mct kit ...` | 数据与文本工具（见下） |
| `mct patch create|apply` | 补丁 |
| `mct test pointer|command|pattern` | 验证 pattern |

`datapack` / `region` / `cext` 的 `extract`、`patch create`、`test command|pattern` 共用同一套 pattern 选项：

| 选项 | 含义 | 内置集 | 关闭内置 | 关闭过滤 |
|---|---|---|---|---|
| `--pattern-mcjson-pattern` | JSON 数据包文本 | `BuiltinMCJsonPatterns` | `--disable-builtin-mcjson` | `--disable-filter-mcjson` |
| `--pattern-nbt-pattern` | region / NBT 文本 | `BuiltinNbtPatterns` | `--disable-builtin-nbt` | `--disable-filter-nbt` |
| `--pattern-command` | 命令结构 | `BuiltinCommandPatterns` | `--disable-builtin-command` | — |
| `--pattern-command-data` | 命令内 SNBT 数据 | `BuiltinCommandDataPatterns` | `--disable-builtin-command-data` | `--disable-filter-command-data` |
| `--pattern-command-component` | 命令内物品组件 | `BuiltinMinecraftComponentPatterns` | `--disable-builtin-command-component` | — |
| `--pattern-command-regex` | 命令裸正则 | 无 | — | — |
| `--pattern-cext` | 按路径自定义 | 无（文件内可 opt-in 预设） | — | — |

合并语义（`CommonCommand.kt` 的 `gatherPattern`）：

| 情况 | 结果 |
|---|---|
| 给了 `--disable-filter-*` | 该层**不过滤**（全部候选通过），内置与自定义都被忽略 |
| 没给 pattern 路径 | 只用内置；若同时给了 `--disable-builtin-*` 则结果为空 |
| 给了 `--disable-builtin-*` + 路径 | 只用自定义文件 |
| 给了路径 | 内置 + 自定义（列表层内置在前；`command` 层自定义在前） |

`command` 与 `command_component` 两层没有「关闭过滤器」选项；只给 `--disable-builtin-*` 而不给 pattern 路径，会直接 panic。

`--disable-filter-mcjson` 会把该层置为 null，并打印一条警告；`--disable-filter-nbt` 与 `--disable-filter-command-data` 同理，各自作用于对应的层。

## 六、`mct kit` 工具

| 命令 | 用途 |
|---|---|
| `kit export-snbt -i <图> -o <目录>` | 把 region 里全部 NBT 导出成 SNBT 文本，便于 `rg` 检索 |
| `kit export-scheme -K (command\|data_pointer\|command_regex) -o <文件>` | 导出 pattern 的 JSON Schema |
| `kit text-pool flatten -i <抽取> -o <池> --kind (datapack\|region) [--simply]` | 抽取组扁平化为文本池 |
| `kit text-pool unflatten -i <抽取> -m <映射> -o <替换组>` | 映射回填为替换组 |
| `kit translate` | 单次 AI 翻译（不走 project 的缓存结构） |
| `kit term-extract` | 单次术语抽取 |
| `kit mtlx translate -m <mtlx> -p <池> -o <映射>` | 用 MTLX 映射文本池 |
| `kit mtlx generate -i <池或映射> -s (pool\|mapping) -o <mtlx>` | 生成 MTLX 模板 |
| `kit official download -mv <版本> -o <目录>` | 下载 Mojang 官方语言文件 |
| `kit official combine -f <源语言> -t <目标语言> -o <术语表>` | 合并出 MCT 术语表（**零 token**） |
| `kit replace-all -i <抽取> -o <输出> -r <文本>` | 把所有抽取结果替换成同一文本（调试用） |
| `kit display <text-component> [-f (json\|snbt\|auto)]` | 渲染文本组件 |
| `kit convert` | NBT ↔ SNBT ↔ JSON 互转 |
| `kit map view\|edit` | 地图文件 ↔ 图片 |

`kit official download` 加 `combine` 是零 token 拿到官方术语表的路径。只在用户要求 100% 遵循 Minecraft 官方译名时使用；你也可以把它当作 agent 自翻时的可选术语来源。

## 七、用补丁分发译文（`project patch` / `mct patch`）

`project build` 是把译文回填到**你自己**项目里的 `build/`。要把译文本地交付给别人用，走补丁：`.mctp` 是一个自包含文件，对方拿同一张地图自己应用即可，不必拿到你的整个项目目录。

### 7.1 生成补丁

在项目里（`project patch` 读 `mappings.json` 与三份抽取缓存）：

```bash
mct project patch
```

产物是 `<项目>/<名称>.mctp`，名称取 `[patch].name`，未配则取项目名。`[patch].kind` 决定补丁形态（默认 `immediate`）。

脱离项目、直接对地图生成：

```bash
mct patch create -i <地图目录> -m <mapping.json> -o <补丁.mctp> \
    [-k (immediate|deferred)] [-f (json|cbor)] [--validation/--no-validation]
```

`patch create` 需要 pattern 与 mapping：pattern 用同一套 `--pattern-*` 选项给（默认内置集），所以它与 `datapack extract` 一样接受 `--pattern-mcjson-pattern` 等参数。

### 7.2 `immediate` 与 `deferred`

| 形态 | 内容 | 体积 | 适用 |
|---|---|---|---|
| `immediate`（默认） | 创建时就把替换组求值好，直接存替换组 | 大 | 对方机器上一应用就完事，最省事 |
| `deferred` | 存 pattern 与 mapping，应用时才求值 | 小 | 体积敏感、或希望对方按自己的 pattern 配置重新求值 |

实测同一张测试地图：`immediate` 7666 字节，`deferred` 4196 字节。两者应用后都能正确回填。

`deferred` 的代价是**应用环境必须能复现抽取**，所以地图必须先构建过、pattern 要能给对；`immediate` 把这一步固化在补丁里，更不容易出错。**不确定时用默认的 `immediate`。**

### 7.3 `json` 与 `cbor`

`-f json`（默认）产出可读、可手改、可进版本库的 JSON；`-f cbor` 是二进制，体积略小（实测 6835 字节对 7666 字节，收益不大）。创建与应用的 `-f` 必须一致。除非有明确理由，用默认的 `json`。

### 7.4 验证与三种策略

`--validation`（默认开）会在补丁里写入**整张地图的文件 SHA1 哈希树**。应用时按 `--validation-strategy` 决定遇到不匹配怎么办：

| 策略 | 行为 |
|---|---|
| `Failure`（应用时默认） | 只要有文件哈希不匹配就**拒绝应用**，并逐条列出差异 |
| `Warning` | 照常应用，但把差异作为告警打印 |
| `Ignore` | 完全跳过校验 |

实测：故意改动 `level.dat` 后，默认策略直接拒绝并报 `Unmatched file: level.dat, expected: 5899f8eb..., but got c3c8de98...`；`Warning` 会应用并告警；`Ignore` 直接应用。

差异只有三种：`Missing`（补丁要求存在但实际没有）、`Redundant`（实际多出来的文件）、`Unmatched`（存在但内容不同）。**这正是补丁只对同一张地图有效的原因**：对方必须用同一版本的地图，改动过就会被拦下。

`--no-validation` 创建出的补丁不带哈希，应用时不做任何检查。只有当你明确希望「不问地图版本、硬套」时才这么做。

### 7.5 对方如何应用

```bash
mct patch apply -i <地图目录> -p <补丁.mctp> [-f (json|cbor)] [--validation-strategy=(Failure|Warning|Ignore)]
```

- `-i` 指向**要应用补丁的那份地图目录**（含 `level.dat`）。补丁是就地修改，**建议对方先备份**。
- 应用后没有单独的成品目录；要核对结果就 `mct kit export-snbt -i <地图目录> -o <目录>` 再检索译文。
- 输出 `Patch was successfully applied` 表示成功；带告警时会是 `... with some warnings as the following` 并列出差异；被拒时是 `The patch has been failed to apply due to the following errors:`。
- 缺文件、多文件、内容不同都会被报出来，所以对方能从输出里直接看出地图版本对不对。

### 7.6 交付建议

- 发布时把 `.mctp`、目标地图版本、以及「用 `--validation-strategy Failure` 应用」这条写进说明；对方地图不对时会明确失败，而不是静默半套用。
- 想让对方只拿一个文件的**把 `.mctp` 附在发布物里**；想让对方能改译文的，给 `json` 格式；只想省体积又不在意可读性的，给 `cbor`。
- 你自己要重新生成成品世界，仍然用 `project build`，不必绕道补丁。

## 八、`mct test`

```bash
mct test pattern [--pattern-* ...] [-c]                    # 打印最终组装出的 pattern 集合
mct test pointer -k (mcjson|region) [-p <pattern>] [--no-builtin] '<指针>'
mct test command -i <mcfunction 文件> [--pattern-* ...]     # 高亮命中范围
```

`test pointer` 输出 `true`/`false`，是验证单条路径最快的办法。`test command` 用颜色高亮打印命中的文本，用来确认抽取边界。

写 pattern 时先按 `pattern-selection.md` 选层，再按对应参考修改；改完必须用 `mct test` 验证，然后重跑 `project update`。

## 九、常见问题

1. **键写错会静默产出 0 组替换**。`project build` 只认 `mappings.json` 里与文本池**逐字相同**的键。键写错时输出 `Generated 0 region replacement groups`，但照样打印 `Build complete` 并给出成品目录，而那个世界是未翻译的。构建后必须核对替换组数量，或者直接在成品世界里检索原文。

2. **value 写 `null` 是「保留原文」的正规手段**。`mappings.json` 的值类型是 `String?`：`null` 表示这一项决定不翻译。`project update` 用**键是否存在**判断是否已处理，所以写了 `null` 的条目不会再出现在 `missing.json`，但 `project build` 不会为它生成替换，成品世界里原文照旧。判定不该翻译时就用 `null` 固化决定，不要留空让它反复出现在 `missing.json`。注意 `null` 与空串 `""` 不同，后者会把原文替换为空。

3. **文本池的键是 IR 编码串，不是裸文本**。`missing.json` / `all_texts.json` 里是 `"\"八千代\""`、`["\"Some texts\"",...]` 这类形式，外层引号、转义、数组结构都是键的一部分。写 `mappings.json` 时**键与值都要保持同一套编码**，只改其中的可见文字。取值不要手写编码：从 `missing.json` 复制键，再就地替换文字。

4. **`missing.json` 不会被清理**。`project update` 只在发现新的未译项（`missingPool` 非空）时才覆写它；全部已译时只打印 `No new items found`，**旧文件原样保留**。所以「`missing.json` 存在」不等于「还有未译项」。判断依据是 `update` 的输出（`Missing N items` 或 `No new items found`），或者文件的修改时间，而不是文件在不在。

5. **`project build` 整体重建 `build/`**。它会先 `deleteRecursively` 再复制 `src/`，所以手动改过 `build/` 里的文件一定会丢。手工修改要落在 `src/` 或 `mappings.json` 上。

6. **`project build` 里 MTLX 结果会覆盖 AI 译文**。设了 `mtlx` 时，`build` 先跑 `translateByMTLX`，再把结果 `+=` 到 mapping 上。同一文本在两处给出不同译法时，MTLX 赢。

7. **`project init` 会整份复制地图**。地图几十 GB 时明显占盘，而且此后每次 `build` 还会再复制一份。项目目录不要放在待翻译的地图内部，否则复制会递归。

8. **报「Source directory is not a valid Minecraft world」时，先怀疑 `-D` 用错了**，而不是路径里的字符。`init` 的 `-D` 是父目录，其余子命令的 `-D` 是项目目录本身；少了或多了这一层，就会去一个没有 `src/level.dat` 的地方找世界。

   含中文、空格、方括号的路径本身可以用：实测非 ASCII 的源路径、项目父目录与项目名都能正常 `init` / `update`。真遇到读不到 `level.dat` 的情况，优先核对 `-D` 指向与 `level.dat` 是否真的存在于该目录；确需改动时也应把项目放在一个稳定的位置，而不是临时目录。

9. **CLI 与 `mct.toml` 的 pattern 语义方向不同**。CLI 的 `--disable-builtin-*` 表示「关掉内置、只用自定义」，且需要路径；`mct.toml` 的 `has_builtin = false` 同义，但不需要额外路径。不要把 CLI 的心智模型直接套到 toml 上，也不要指望 toml 支持 `--disable-filter-*` 那种「全部通过」的调试模式；需要全量抽取时用 CLI。

10. **`--cache-dir` 默认是当前目录**。project 流程不读它（缓存固定在项目内的 `cache/`），但直接跑 `datapack` / `region` / `cext` 子命令时会在当前目录留下缓存。批处理前先确认工作目录。
