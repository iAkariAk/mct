# Skill: Add Pattern Tests

Trigger when the task adds, removes, or modifies patterns in `mct/dp/mcjson/BuiltinPatterns.kt`, `mct/dp/mcfunction/Patterns.kt`, or `mct/region/Patterns.kt`. After changing patterns, you MUST add or update tests in the corresponding test file.

## Test File Map

| Pattern file | Test file | Test class |
|---|---|---|
| `mct/dp/mcjson/BuiltinPatterns.kt` | `mct/.../mct/dp/mcjson/MCJDataPointerPatternTest.kt` | `MCJDataPointerPatternTest` |
| `mct/dp/mcfunction/Patterns.kt` | `mct/.../mct/command/CommandExtractPatternTest.kt` | `CommandExtractPatternTest` |
| `mct/region/Patterns.kt` | `mct/.../mct/nbt/NbtDataPointerPatternTest.kt` | `NbtDataPointerPatternTest` |

**Helper pattern:** Each test file binds the pattern set using `::shouldMatch.partially2(Patterns)`, so test cases are single-line:

```kotlin
class NbtDataPointerPatternTest : FreeSpec({
    val shouldMatch = ::shouldMatch.partially2(BuiltinNbtPatterns)
    val shouldNotMatch = ::shouldNotMatch.partially2(BuiltinNbtPatterns)

    "BuiltinNbtPatterns" - {
        "match entity CustomName" {
            shouldMatch(">#>#Entities>0>#CustomName")
        }
        "not match unrelated path" {
            shouldNotMatch(">#unrelated>#path>#here")
        }
    }
})
```

**Shared helpers** are defined in `mct/pointer/DataPointerPatternTest.kt` (package `mct.pointer`):
- `ptr(s)` — parses a DataPointer string (asserts no error)
- `ptr.shouldMatch(patterns)` / `ptr.shouldNotMatch(patterns)` — assertion infix
- `shouldMatch(ptr, patterns)` / `shouldNotMatch(ptr, patterns)` — top-level version

**CI command:** `./gradlew :mct:jvmTest`

## All Test Files (for reference)

| File | Package | What it tests |
|---|---|---|
| `mct/pointer/DataPointerPatternTest.kt` | `mct.pointer` | DataPointer.matchesRight, matches(Regex), PatternSet DSL, CustomizedDataPointerPattern |
| `mct/nbt/NbtDataPointerPatternTest.kt` | `mct.nbt` | BuiltinNbtPatterns (region patterns) |
| `mct/nbt/NbtExtractPatternTest.kt` | `mct.nbt` | NBT extraction pattern selection |
| `mct/dp/mcjson/MCJDataPointerPatternTest.kt` | `mct.dp.mcjson` | BuiltinMCJPatterns (datapack JSON patterns) |
| `mct/command/CommandExtractPatternTest.kt` | `mct.command` | BuiltinCommandPatterns, BuiltinCommandDataPatterns |
| `mct/command/CommandsTest.kt` | `mct.command` | Full extraction→backfill pipeline integration tests |
