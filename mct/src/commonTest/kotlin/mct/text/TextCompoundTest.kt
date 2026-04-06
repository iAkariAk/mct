package mct.text


import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import mct.serializer.MCTJson
import mct.serializer.NbtNone
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString

class TextCompoundSerializerTest : StringSpec({
    val json = Json(MCTJson) {
        prettyPrint = false
    }
    val nbt = NbtNone
    val serializer = TextCompoundSerializer()

    "@Serialization" {
        shouldNotThrowAny {
            json.decodeFromString<TextCompound>("\"hi\"")
        }
    }

    "deserialize plain string" {
        val input = "\"hello\""

        val result = json.decodeFromString(serializer, input)

        result shouldBe TextCompound.Plain("hello")
    }

    "serialize plain string" {
        val value = TextCompound.Plain("hello")

        val result = json.encodeToString(serializer, value)

        result shouldBe "\"hello\""
    }

    "deserialize text object" {
        val input = """{"text":"hello"}"""

        val result = json.decodeFromString(serializer, input)

        result shouldBe TextCompound.Plain("hello")
    }

    "serialize text object (non-plain)" {
        val value = TextCompound.Plain("hello", color = "red")

        val result = json.encodeToString(serializer, value)

        result shouldBe """{"text":"hello","color":"red"}"""
    }

    "deserialize array to extra chain" {
        val input = """["a","b","c"]"""

        val result = json.decodeFromString(serializer, input)

        result shouldBe TextCompound.Plain(
            "a",
            extra = listOf(
                TextCompound.Plain("b"),
                TextCompound.Plain("c")
            )
        )
    }

    "serialize extra chain to array" {
        val value = TextCompound.Plain(
            "a",
            extra = listOf(
                TextCompound.Plain("b"),
                TextCompound.Plain("c")
            )
        )

        val result = json.encodeToString(serializer, value)

        result shouldBe """["a","b","c"]"""
    }

    "flatten nested extra when serializing" {
        val value = TextCompound.Plain(
            "a",
            extra = listOf(
                TextCompound.Plain(
                    "b",
                    extra = listOf(TextCompound.Plain("c"))
                )
            )
        )

        val result = json.encodeToString(serializer, value)

        result shouldBe """["a","b","c"]"""
    }

    "deserialize nested array" {
        val input = """["a", ["b","c"]]"""

        val result = json.decodeFromString(serializer, input)

        result shouldBe TextCompound.Plain(
            "a",
            extra = listOf(
                TextCompound.Plain("b"),
                TextCompound.Plain("c")
            )
        )
    }

    "serialize object with extra to array" {
        val value = TextCompound.Translatable(
            translate = "chat.type.text",
            with = listOf(TextCompound.Plain("Akari")),
            extra = listOf(TextCompound.Plain("!!!"))
        )

        val result = json.encodeToString(serializer, value)

        result shouldBe """{"translate":"chat.type.text","with":["Akari"],"extra":"!!!"}"""
    }

    "fail on non-string primitive" {
        val input = "123"

        shouldThrow<IllegalStateException> {
            json.decodeFromString(serializer, input)
        }
    }

    "fail on null" {
        val input = "null"

        shouldThrow<IllegalStateException> {
            json.decodeFromString(serializer, input)
        }
    }

    "deserialize NBT string" {
        val tag = NbtString("hello")

        val result = nbt.decodeFromNbtTag(serializer, tag)

        result shouldBe TextCompound.Plain("hello")
    }

    "serialize NBT string" {
        val value = TextCompound.Plain("hello")

        val result = nbt.encodeToNbtTag(serializer, value)

        result shouldBe NbtString("hello")
    }

    "deserialize NBT list" {
        val tag = NbtList(
            listOf(
                NbtString("a"),
                NbtString("b"),
                NbtString("c")
            )
        )

        val result = nbt.decodeFromNbtTag(serializer, tag)

        result shouldBe TextCompound.Plain(
            "a",
            extra = listOf(
                TextCompound.Plain("b"),
                TextCompound.Plain("c")
            )
        )
    }

    "serialize NBT list" {
        val value = TextCompound.Plain(
            "a",
            extra = listOf(
                TextCompound.Plain("b"),
                TextCompound.Plain("c")
            )
        )

        val result = nbt.encodeToNbtTag(serializer, value)

        result shouldBe NbtList(
            listOf(
                NbtString("a"),
                NbtString("b"),
                NbtString("c")
            )
        )
    }

    "json round trip" {
        val original = TextCompound.Plain(
            "a",
            extra = listOf(
                TextCompound.Plain("b"),
                TextCompound.Plain("c")
            )
        )

        val encoded = json.encodeToString(serializer, original)
        val decoded = json.decodeFromString(serializer, encoded)

        decoded shouldBe original
    }

    "nbt round trip" {
        val original = TextCompound.Plain(
            "a",
            extra = listOf(
                TextCompound.Plain("b"),
                TextCompound.Plain("c")
            )
        )

        val encoded = nbt.encodeToNbtTag(serializer, original)
        val decoded = nbt.decodeFromNbtTag(serializer, encoded)

        decoded shouldBe original
    }
})
