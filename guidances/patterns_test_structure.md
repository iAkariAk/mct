# Pattern Test Structure

Use this guidance whenever a task adds, removes, or modifies builtin patterns.

## Pattern File Map

| Pattern file | Test file | Test class |
|---|---|---|
| `mct/src/commonMain/kotlin/mct/dp/mcjson/BuiltinPatterns.kt` | `mct/src/commonTest/kotlin/mct/dp/mcjson/MCJDataPointerPatternTest.kt` | `MCJDataPointerPatternTest` |
| `mct/src/commonMain/kotlin/mct/command/BuiltinPatterns.kt` | `mct/src/commonTest/kotlin/mct/command/CommandExtractPatternTest.kt` | `CommandExtractPatternTest` |
| `mct/src/commonMain/kotlin/mct/nbt/BuiltinPatterns.kt` | `mct/src/commonTest/kotlin/mct/nbt/NbtDataPointerPatternTest.kt` | `NbtDataPointerPatternTest` |

After changing any builtin pattern set, update the corresponding test file.

## BuiltinSet Style

Builtin path-pattern tests should be one aggregate `BuiltinSet` test, not one Kotest node per path.

```kotlin
class NbtDataPointerPatternTest : FreeSpec({
    "BuiltinNbtPatterns BuiltinSet" {
        listOf(
            match(">#display>#Name", "item display Name"),
            match(">#display>#Lore", "item display Lore"),
            notMatch(">#unrelated>#path>#here", "unrelated path"),
        ).test(BuiltinNbtPatterns)
    }
})
```

Shared helpers live in `mct/src/commonTest/kotlin/mct/pointer/DataPointerPatternTest.kt`:

- `match(path, name)` — expected to match.
- `notMatch(path, name)` — expected not to match.
- `List<PointerPatternCase>.test(patterns)` — runs every case, collects every failure, then fails once with the complete list.
- `ptr(s)` — parses a DataPointer string for low-level tests.

The point of this shape is that one broken pattern must not stop later pattern cases from running. The failure output should show all broken cases in one report.

## Command BuiltinSet Style

`BuiltinCommandPatterns` tests use the same aggregate idea, but cases are command strings rather than pointer paths.

Use two assertion modes:

- expected contents omitted: assert that at least one pattern matched.
- expected contents provided: assert exact extracted content order.

Example:

```kotlin
listOf(
    commandCase("say command", "say Hello world", "Hello world"),
    commandCase("give with text component", """give @p stick{display:{Name:"text"}}"""),
).test()
```

If a new command pattern has an exact stable extracted string, include it. If the purpose is only to prove that a nested SNBT pattern path is reached, omit expected contents and assert non-empty extraction.

## Low-Level Tests Stay Separate

Keep low-level behavior tests outside the BuiltinSet table:

- `DataPointer.matchesRight`
- regex matching behavior
- `PatternSet` DSL behavior
- `CustomizedDataPointerPattern.compile`
- command condition and selector primitives
- target selector intrinsic extraction
- recursive subcommand extraction
- greedy range behavior

These tests describe mechanics. BuiltinSet tests describe builtin catalog coverage.

## Validation

Primary command:

```bash
./gradlew :mct:jvmTest
```

In this Windows workspace, Gradle may need access to `D:\tools\.gradle`. If the wrapper fails on the Gradle lock file inside the sandbox, rerun the same command with escalation.
