# Pattern Generation Guide

**Process note:** After completing any task that adds, removes, or modifies patterns in ANY of the three files below, update this guidance file to reflect the new knowledge gained. This includes: findings from wiki audits, pitfalls discovered, DSL features used, pattern strategies that worked/didn't work, and changes to `isTextCompound()` / `ALL_FIELD` / `STRUCTURAL_FIELDS` in `Util.kt`.

---

Write more patterns for the following files. Each file uses a different pattern DSL — refer to the existing patterns in that file and the DSL definitions before adding new ones.

## Files

### 1. `mct/src/commonMain/kotlin/mct/dp/mcjson/BuiltinPatterns.kt`

**DSL:** `PatternSet { +RightPattern(...); +RegexPattern(...) }` from `mct.pointer`

**Purpose:** Extract translatable text from data pack `.json` files (advancements, loot tables, jukebox songs, trim patterns, etc.)

**Key insight:** The `extractTextMCJ()` function extracts ALL string leaf values from JSON, then **filters** by patterns. A `RightPattern` checks if the NBT pointer path **ends with** the given string. A `RegexPattern` matches the full encoded path.

**How paths work:**
- For `{"display": {"title": {"text": "Hello"}}}`, the leaf string `"Hello"` is at path `>#display>#title>#text`
- A `RightPattern("#text")` matches any path ending in `#text` (covers all text component values)
- A `RightPattern("#description")` matches root-level descriptions as plain strings
- A `RegexPattern("""#description>#(?:text|translate|fallback)$""")` matches text component description leaves

**References:**
- https://datapack-wiki.pages.dev/
- https://minecraft.wiki/w/Data_pack — overall structure, all registry JSON types
- https://minecraft.wiki/w/Text_component_format — how text components work (check for new fields)

**Existing coverage:** Advancements (title/description), item components (custom_name/lore), signs, books, loot table functions, CustomName, jukebox description, trim/banner description, painting variant title/author, loot table translate variants, set_attributes modifier names

**Look for:** Any registry JSON with text component fields that aren't yet matched. Good candidates have `"text"`, `"translate"`, `"fallback"` fields or plain string descriptions. Common registries: `painting_variant`, `wolf_variant` (1.21.2+), `instrument`, `feat` (1.21.5+). When a text component uses `"translate"`, also include the `"fallback"` path since both are leaf nodes.

---

### 2. `mct/src/commonMain/kotlin/mct/dp/mcfunction/Patterns.kt` + `MCFunction.kt`

**DSL:** `PatternSet { command("name") { WithSize(N).then { Positions(N).then { Matches {...} } } } }` from `mct.dp.mcfunction`

**Purpose:** Extract translatable text from `.mcfunction` command files.

**Key insight:** The system parses each line as an `MCCommand(name, args)`. Patterns specify:
1. **PreCondition** — e.g. `WithSize(N)` (≥N args), `WithSize(N, strict=true)` (exactly N args), `Any()`, `Regex("...")` (matches raw), or `And(...)` / `Or(...)` to combine
2. **IndexSelector** — which arg(s) to inspect: `Positions(N)` (specific position), `GreedyPositions(N)` (from position N to end, **no postCondition applied for greedy selectors**), or `Positions(N to IndexSelection.SnbtEntire)` (parse as SNBT and extract sub-texts via data patterns)
3. **PostCondition** — e.g. `Matches { cmd, arg -> ... }` (custom predicate), `Regex("...")`, `Contain("...")`

**Argument parsing details:** The MCFunction parser tracks bracket states (`[]`, `{}`) and quote states (`'`, `"`) globally, so `@e[tag=foo]` or `{"text":"hello"}` each count as a single arg. The `MCCommand.get(Int)` operator is **1-based**: `cmd[1]` = `args[0]`, `cmd[2]` = `args[1]`, etc.

**SNBT selection (`IndexSelection.SnbtEntire`):**
When `Positions(N to IndexSelection.SnbtEntire)` is used, the arg at position N is parsed as SNBT, then `SnbtTag.extractTexts()` extracts text components and leaf strings from the NBT tree. Results are filtered through `BuiltinMCFunctionDataPatterns`. Only `FormatKind.Nbt` entries survive the final filter — plain strings (FormatKind.Str) from NBT are **excluded**.

Use `withAry()` on the index selector to apply `BuiltinMCFunctionDataPatterns` (which has specific patterns for display entity text, CustomName, and dialog SNBT fields). Without `withAry()`, use a `Matches` postCondition to filter manually.

**Common commands with text components (Java Edition):**

| Command | Wiki Syntax | Text Position | Strategy |
|---------|-------------|---------------|----------|
| `tellraw` | `<targets> <message>` | 2 | `Positions(2)` |
| `title` | `<targets> (title\|subtitle\|actionbar) <component>` | 3 (action != "times") | `Positions(3)` + Matches { not times } |
| `bossbar add` | `<id> <name>` | 3 | `Positions(3)` + Matches { subcmd=add } |
| `bossbar set name` | `<id> name <component>` | 4 | `Positions(4)` + Matches { subcmd=set, field=name } |
| `scoreboard objectives add` | `<objective> <criteria> [<displayName>]` | 4 | `Positions(4)` + Matches { objectives add } |
| `scoreboard objectives modify ... displayname` | `<objective> displayname <component>` | 5 | `Positions(5)` + Matches |
| `scoreboard ... numberformat fixed` | `<objective> numberformat fixed <component>` | 6 | `Positions(6)` + Matches |
| `scoreboard players display name` | `<targets> <objective> <text>` | 6 | `Positions(6)` + Matches |
| `scoreboard players display numberformat fixed` | `<targets> <objective> fixed <contents>` | 7 | `Positions(7)` + Matches |
| `team add` | `<team> [<displayName>]` | 3 | `Positions(3)` + Matches { add } |
| `team modify displayName` | `<team> displayName <component>` | 4 | `Positions(4)` + Matches { displayName } |
| `team modify prefix/suffix` | `<team> (prefix\|suffix) <component>` | 4 | `Positions(4)` + Matches { prefix/suffix } |
| `data modify ... set value` | `<target> [<path>] set value <json>` | 6-7 | `Positions(N)` + isTextComponent |
| `data merge entity/storage` | `<target> <nbt>` | 4 (SnbtEntire) | `And(WithSize(4), Regex("merge (entity\|storage)"))` + `Positions(4 to SnbtEntire)` |
| `data merge block` | `<pos> <nbt>` | 6 (SnbtEntire) | `And(WithSize(6), Regex("merge block"))` + `Positions(6 to SnbtEntire)` |
| `summon` | `<entity> [<pos>] [<nbt>]` | 5 (SnbtEntire) | `Positions(5 to SnbtEntire).withAry()` |
| `setblock` | `<pos> <block> [destroy\|keep\|replace]` | 5 (SnbtEntire) | `WithSize(5)` + `Positions(5 to SnbtEntire)` + Matches { startsWith("{") } |
| `give` | `<targets> <item>` | 2 | `Positions(2)` + Matches { contains component markers } |
| `dialog show` | `<targets> <dialog>` | 3 (SnbtEntire, inline SNBT only) | `Positions(3 to SnbtEntire)` + Matches { show && startsWith("{") } |
| `kick` | `<targets> [<reason>]` | greedy 2 | `GreedyPositions(2)` (message type, plain text) |
| `say`, `me`, `teammsg` | `<message>` | greedy 0 | `GreedyPositions()` |
| `msg`, `tell`, `w` | `<targets> <message>` | greedy 2 | `GreedyPositions(2)` |

**Commands that do NOT accept text components (verified against wiki):**
- `spreadplayers` — Only takes vec2, floats, bool, entities. No description/component arg.
- `waypoint` — Only `list` and `modify color/style`. No `add` subcommand or displayComponent.
- `fill` / `place` / `damage` / `kill` — None accept text components.
- `execute run` / `return run` — Handled recursively, no direct pattern needed.

**For commands that wrap subcommands** (`execute run <cmd>`, `return run <cmd>`), recursive extraction is handled in `MCFunction.kt`'s `extractTextFromCommand()` — the subcommand is re-parsed and patterns are applied recursively. No pattern for the wrapping command itself is needed.

**Data path for `Positions(N to IndexSelection.SnbtEntire)` without `withAry()`:**
If you use `Positions(N to SnbtEntire)` + a `Matches` postCondition (without `withAry()`), the flow is:
1. PostCondition filters the raw arg
2. SnbtEntire tries to parse the arg as SNBT
3. If SNBT parsing succeeds, text component leaves are extracted and filtered through `BuiltinMCFunctionDataPatterns`; the `filter { it.kind == FormatKind.Nbt }` step removes plain strings
4. If SNBT parsing fails, falls back to `PlainEntire` which returns the whole arg (which then gets extracted as-is if Matches passed)

**References:**
- https://minecraft.wiki/w/Commands — full command reference
- Pay attention to commands that accept JSON text components

**Existing coverage:** say, me, teammsg, msg/tell/w, tellraw, title, dialog, bossbar, scoreboard, team, data, give, item, kick, summon, setblock, data merge (16 command groups; waypoint and spreadplayers removed per wiki audit — they have no text component args)

**Look for:** Commands that accept JSON text components or NBT with text that aren't yet covered. Before adding a pattern, verify the exact wiki syntax at https://minecraft.wiki/w/Commands/<command> — many commands that "seem like" they accept text components actually don't (e.g. spreadplayers, waypoint, damage, kill, place, fill). Always check the wiki first.

---

### 3. `mct/src/commonMain/kotlin/mct/region/Patterns.kt`

**DSL:** `PatternSet { +RightPattern(...); +RegexPattern(...) }` from `mct.pointer`

**Purpose:** Extract translatable text from Minecraft region files (`.mca`) — chunk NBT data.

**Key insight:** The `extractTexts()` function walks NBT recursively and produces pointer paths for leaf strings. Patterns filter which paths to keep. The format differs from MCJSON:
- Region NBT uses SNBT format
- Text components in NBT are detected via `isTextCompound()` / `isTextCompoundShorthanded()` — when a compound contains ONLY known text-component fields AND non-structural fields have primitive values, it extracts the whole compound as SNBT rather than recursing into it
- Item stacks within containers/entities are recursively walked — their display/components are matched by existing item patterns

**Important: isTextCompound() value type check**
The function `isTextCompound()` in `Util.kt` requires:
1. All keys are in `ALL_FIELD` (known text-component field names)
2. For non-structural fields (`text`, `translate`, `color`, `bold`, etc.), the value must be a primitive type (string, boolean, number), NOT a compound or list
3. Structural fields (`extra`, `with`, `hover_event`, `click_event`, `score`, `separator`, `player`, `shadow_color`) may hold compound/list values

**NbtList isTextCompound() (New):**
Since the latest refactor, `NbtList<*>.isTextCompound()` also works — if a list contains only text-component elements (strings or nested text compounds), it is extracted as a single SNBT block rather than recursed into individual elements. This mirrors the `NbtCompound` behavior and prevents path explosion for uniform text-component lists.

**Current `ALL_FIELD` fields (keep in sync with `Util.kt`):**
```
text, translate, with, fallback,
score, selector, keybind,
nbt, block, entity, storage,
interpret, plain, separator, source,
object, sprite, atlas, player, hat,     // hat added in 1.21.5+ object:"player"
extra, type,
color, font,
bold, italic, underlined, strikethrough, obfuscated,
shadow_color, insertion,
click_event, hover_event
```

Structural fields (allow compound/list values): `extra`, `with`, `hover_event`, `click_event`, `score`, `separator`, `player` (profile data), `shadow_color` (can be [R,G,B,Opacity]).

This prevents `{text:["hello","world"]}` from being incorrectly treated as a text component (where `text` has a list value). Without this check, the entire root would be extracted at the empty root path (`DataPointer.Terminator`), discarding internal structure and never matching any `>#text`-based pattern.

**Encoding specifics for NBT paths:**
- Entity arrays: `>#>#Entities>\d+>...` (stored entities)
- Block entity arrays: `>#>#block_entities>\d+>...` (tile entities)
- Items: path ends with `#display>#Name` / `#display>#Lore` (legacy) or `#components>#minecraft:custom_name` (modern)
- NBT key names are case-sensitive (PascalCase is common: `CustomName`, `SpawnData`, `EntityData`, etc.)

**Pattern anchoring notes:**
- Existing component patterns (`#components>#minecraft:custom_name`) don't have `^` anchors — they match anywhere in the path, so they work for both entity and block_entity paths, and for deeply nested data components
- New patterns with `^...$` anchors are more precise but need explicit entity/block_entity prefix

**Data component patterns (non-anchored, match inside `#components>#minecraft:*`):**
These patterns match text components nested within data components. The non-anchored approach lets them match at any depth:
- `#components>#minecraft:custom_name` — display name (item/entity)
- `#components>#minecraft:item_name` — default item name
- `#components>#minecraft:text_display` — text display entity data
- `#components>#minecraft:description` — 1.21.5+ entity/item description
- `#components>#minecraft:lore>(\d+>#raw)?$` — item lore lines (also matches bare `lore` key via `(\d+>#raw)?` being optional)
- `#components>#minecraft:written_book_content>#(?:pages>\d+|title|author)(?:>#(?:raw|filtered))?$` — book content
- `#components>#minecraft:writable_book_content>#pages>\d+(?:>#(?:raw|filtered))?$` — writable book pages
- `#instrument>#description` — instrument description (goat horns)
- `#attribute_modifiers>#modifiers>N>#display>#value` — custom attribute display text

**Sign block entity patterns:**
- `>#block_entities>\d+>#(front|back)_text>#(filtered_)?messages(>\d+>#raw)?$` — matches both the messages key itself and individual message entries with optional raw. The `(>\d+>#raw)?` being optional makes the pattern more flexible.

**References:**
- https://minecraft.wiki/w/Block_entity_format — NBT for all block entities (checked: most CustomName via common pattern, signs, spawners, beehives, command blocks)
- https://minecraft.wiki/w/Entity_format — NBT for all entities (checked: all have CustomName via entity regex)
- https://minecraft.wiki/w/Item_format — item NBT (display, components)
- https://minecraft.wiki/w/Data_component_format — modern component system (1.20.5+; checked all text-bearing components)
- https://minecraft.wiki/w/Text_component_format — ALL_FIELD reference for isTextCompound()

**Existing coverage:** Item display (legacy + modern components), entity CustomName (including spawn data / trial spawner configs), sign text (front+back, filtered), written books, container CustomName, display entity text+raw_text+description, beehive entity data, command block CustomName+LastOutput, block entity data components, instrument.description, attribute_modifiers display value

**Known covered by recursive item walk (no explicit patterns needed):**
- Items within all containers (chests, barrels, shulker boxes, hoppers, dispensers, droppers, furnaces, smokers, blast furnaces, brew stands, campfires, chiseled bookshelves, crafter)
- Entity equipment (armor, hands, inventory)
- Villager/wandering trader trade offers
- Minecart/boat with chest
- Item frames, jukebox RecordItem, decorated pot item, brushable block item, vault key_item
- Lectern Book
- bundle_contents, container list, charged_projectiles

**When adding a pattern, verify the exact NBT key casing** (Minecraft NBT typically uses PascalCase: `CustomName`, not `custom_name`). Signal-to-noise matters — avoid patterns so broad they'd match non-translatable strings.

---

## Backfill

### `mct/src/commonMain/kotlin/mct/nbt/Backfill.kt`

The `NbtTag.transform()` function applies replacement groups back into NBT. Since the latest refactor:

- **`decodeTerminatorOrNull<T>()` helper** — shared by both `NbtList` and `NbtCompound` to decode a `Terminator` replacement as type `T`. If decoding fails, it logs an error and returns null.
- **`NbtList` now also handles `Terminator`** — previously only `NbtCompound` could be fully replaced by a `Terminator` replacement. Now `NbtList` also checks for a matching `Terminator` replacement (with `FormatKind.Nbt`) and replaces the entire list if found.
- **`NbtString` fallthrough** — unchanged: if a `Terminator` with a string kind is present, the string is replaced; otherwise the original value is kept.

This means backfill can now handle cases where the extraction produced a list-level text component (via the new `NbtList.isTextCompound()` extraction path).

---

## Testing

**Test structure, file map, and mandatory test rules:** See `guidances/patterns_test_structure.md`.

Test command:
```sh
./gradlew :mct:jvmTest
```
```sh
# Build the CLI jar
./gradlew :cli:shadowJar

# Test datapack extraction
java -jar cli/build/libs/cli-0.0-SNAPSHOT-all.jar datapack extract \
  -i "/path/to/world" -o "/path/to/output.json"

# Test region extraction
java -jar cli/build/libs/cli-0.0-SNAPSHOT-all.jar region extract \
  -i "/path/to/world" -o "/path/to/output.json"
```

### Debugging extraction counts
When the SNBT selection test (`test snbt selecting`) counts change, investigate by:
1. Checking which files/functions have changed extraction counts
2. Understanding that `isTextCompound()` determines whether the root NBT is extracted as a whole vs recursed into
3. Verifying `BuiltinMCFunctionDataPatterns` paths match the actual pointer paths produced by `SnbtTag.extractTexts()`

### Common pitfalls

**1. `isTextCompound()` returning true unexpectedly** — When adding a data merge/setblock/summon pattern, if the root NBT happens to have only text-component keys AND the values happen to be primitive, `isTextCompound()` returns true and the entire root is extracted as one text at the empty path (`DataPointer.Terminator`). This path won't match `>#text` patterns. The fix: ensure `isTextCompound()` correctly rejects compounds with non-structural complex values.

**2. `NbtList.isTextCompound()` can also fire** — Since the refactor, `NbtList` also checks `isTextCompound()`. A list containing only text-component elements gets extracted as a single SNBT block. This is correct behavior but can change extraction counts — if you see unexpected changes, check whether a list that previously yielded N individual paths now yields 1 combined text.

**3. `RightPattern("")` is NOT a root matcher** — `matchesRight("")` calls `encodeToString().endsWith("")`, which is always true for any pointer. Use `RegexPattern("^$")` if you need to match the empty root path specifically.

**4. `Positions(N)` is 1-based** — `cmd[1]` in the Matches callback is `args[0]` (0-based). `Positions(5)` selects the 5th arg (1-based) = `args[4]` (0-based).

**5. Greedy selectors ignore postConditions** — `GreedyPositions(N)` extracts from position N to end of command without checking the postCondition. Use `NonGreedy` (`Positions`) with `Matches` if you need conditional extraction.

**6. FormatKind filter in SNBT selection** — `selectSnbt()` filters extracted texts with `filter { it.kind == FormatKind.Nbt }`, which removes plain strings (FormatKind.Str) from NBT. Only text compounds survive this filter. Notably, `FormatKind.Str` entries from `SnbtString` values (e.g. `CustomName:'{"text":"hello"}'` stored as JSON string) are killed — they must be NBT compounds (e.g. `CustomName:{"text":"hello"}`) to be extracted as Nbt.

**7. NbtList backfill with Terminator** — When a `NbtList` is extracted as a text component (via the new `NbtList.isTextCompound()` path), the backfill expects a `Terminator` replacement with `FormatKind.Nbt`. The `transform()` function now handles this case — `NbtList` branches check `decodeTerminatorOrNull<NbtList<NbtTag>>()` first before processing individual elements.

**8. Always check the wiki first before adding a command pattern** — Many commands that seem like they'd accept text components actually don't (waypoint, spreadplayers, damage, kill, fill, place). Verify at https://minecraft.wiki/w/Commands/<command>.

**9. After completing any pattern task, update this guidance file** — Record new pitfalls discovered, changes to `Util.kt` ALL_FIELD/STRUCTURAL_FIELDS, new pattern strategies, wiki pages researched, and commands/entities/block-entities verified as not having text components (to avoid re-checking).

## Translator Rules

### `extra/src/commonMain/kotlin/mct/extra/ai/translator/Translator.kt`

The translator prompt includes these key constraints:
- **No merging across semantic boundaries** (e.g. text wrapped by clickEvent/hoverEvent)
- **No cross-line reordering** — line numbers must align exactly
  - Example: `[0] BRING MIKE` + `[1] IF YOU GO TO` + `[2] THE SEWERS` must translate line-by-line, not be merged into one sentence
- **No extra content** in output (no blank lines, no comments)
- **String rules:** `%s`, `%d`, color codes (§), etc. must be preserved; literal `@` must remain `@` unless it starts a translation key
