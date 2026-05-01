package mct.text

import mct.util.formatir.*

fun List<TextCompound>.simplify(vararg prefixes: IRElement, remainList: Boolean = false): IRElement? {
    val compounds = this
    return if (compounds.isEmpty()) IRList(prefixes.asList()) else {
        compounds.fold(prefixes.toMutableList()) { acc, compound ->
            when (val r = compound.encodeToIR(true)) {
                is IRList -> acc.addAll(r)
                else -> acc.add(r)
            }
            acc
        }
    }.let {
        when (it.size) {
            0 -> null
            1 if !remainList -> it.first()
            else -> IRList(it)
        }
    }
}

private fun IRObjectBuilder.commonPut(value: TextCompound, simplify: Boolean = true) {
    putIfPresent("extra", if (simplify) value.extra.simplify() else value.extra.map { it.encodeToIR(simplify) }.let(::IRList))
    putIfPresent("color", value.color)
    putIfPresent("bold", value.bold)
    putIfPresent("italic", value.italic)
    putIfPresent("underlined", value.underlined)
    putIfPresent("strikethrough", value.strikethrough)
    putIfPresent("obfuscated", value.obfuscated)
}


fun TextCompound.encodeToIR(simplify: Boolean = true): IRElement {
    val value = this

    return when (this) {
        is TextCompound.Plain -> {
            if (isPlainStyle()) {
                val self = IRString(text)
                if (extra.isEmpty()) self else {
                    extra.simplify(self)!!
                }
            } else {
                buildIRObject {
                    put("text", text)
                    commonPut(value)
                }
            }
        }

        is TextCompound.Translatable -> buildIRObject {
            put("translate", translate)
            this.putIfPresent("fallback", fallback)
            putIfPresent("with", with.simplify(remainList = true))
            commonPut(value)
        }


        is TextCompound.Keybind -> buildIRObject {
            put("keybind", keybind)
            commonPut(value)
        }

        is TextCompound.Nbt -> buildIRObject {
            put("nbt", nbt)
            put("interpret", interpret)
            putIfPresent("separator", separator?.encodeToIR(simplify))
            putIfPresent("entity", entity)
            putIfPresent("block", block)
            putIfPresent("storage", storage)
            commonPut(value)
        }

        is TextCompound.Object -> buildIRObject {
            put("object", `object`)
            putIfPresent("fallback", fallback)
            commonPut(value)
        }

        is TextCompound.Score -> buildIRObject {
            put("score", buildIRObject {
                put("name", score.name)
                put("objective", score.objective)
            })
            commonPut(value)
        }

        is TextCompound.Selector -> buildIRObject {
            put("selector", selector)
            putIfPresent("separator", separator?.encodeToIR(simplify))
            commonPut(value)
        }

        is TextCompound.Sprite -> buildIRObject {
            put("sprite", sprite)
            commonPut(value)
        }
    }
}
