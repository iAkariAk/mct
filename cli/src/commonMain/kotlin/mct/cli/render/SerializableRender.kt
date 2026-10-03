@file:OptIn(ExperimentalSerializationApi::class)

package mct.cli.render

import com.github.ajalt.mordant.rendering.OverflowWrap
import com.github.ajalt.mordant.rendering.TextAlign.LEFT
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.rendering.VerticalAlign.MIDDLE
import com.github.ajalt.mordant.rendering.Widget
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.OrderedList
import com.github.ajalt.mordant.widgets.Text
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import kotlinx.serialization.serializer
import mct.serializer.MCTJson
import mct.util.peek
import mct.util.peekOrNull
import mct.util.pop
import mct.util.push

private sealed interface EncoderState
private data class CollectionState(
    val size: Int,
    var index: Int = 0,
    val list: MutableList<Widget> = mutableListOf()
) : EncoderState {
}

private data class StructureState(
    val map: MutableMap<String, Widget> = mutableMapOf(),
    var key: String? = null
) : EncoderState

private data object RootState : EncoderState

private class WidgetEncoder : AbstractEncoder() {
    override val serializersModule = MCTJson.serializersModule
    var result: Widget? = null
    var states = ArrayDeque<EncoderState>().apply {
        push(RootState)
    }

    private fun encodeWidget(widget: Widget) {
        when (val state = states.peekOrNull()) {
            is CollectionState -> {
                state.list.add(widget)
                state.index++
            }

            is StructureState -> {
                state.map[state.key!!] = widget
            }

            is RootState -> result = widget

            else -> error("Unexpected state: ${states}}")
        }
    }

    override fun encodeValue(value: Any) {
        encodeWidget(Text(value.toString()))
    }

    override fun encodeNull() {
        encodeWidget(Text(red("null")))
    }

    override fun encodeElement(descriptor: SerialDescriptor, index: Int): Boolean {
        when (val state = states.peek()) {
            is StructureState -> state.key = descriptor.getElementName(index)
            else -> {}
        }
        return true
    }

    override fun beginCollection(descriptor: SerialDescriptor, collectionSize: Int): CompositeEncoder {
        states.push(CollectionState(collectionSize))
        return this
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder {
        states.push(StructureState())
        return this
    }

    override fun endStructure(descriptor: SerialDescriptor) {
        when (val state = states.pop()) {
            is StructureState -> encodeWidget(table {
                borderType = SQUARE_DOUBLE_SECTION_SEPARATOR
                borderStyle = blue
                align = LEFT

                header {
                    style = bold + blue
                    row("", descriptor.serialName)
                }

                body {
                    style = green
                    state.map.forEach { (k, v) ->
                        row {
                            cell(k) {
                                overflowWrap = OverflowWrap.ELLIPSES
                                verticalAlign = MIDDLE
                            }
                            cell(v) {
                                align = LEFT
                            }
                        }
                    }
                }
            })

            is CollectionState -> {
                encodeWidget(OrderedList(state.list))
            }

            else -> error("Unexpected state: ${state}}")
        }
    }
}

class SerializableRender<T>(private val serializer: SerializationStrategy<T>) : Render<T> {
    override fun render(value: T, terminal: Terminal): String {
        val encoder = WidgetEncoder()
        serializer.serialize(encoder, value)
        val result = encoder.result!!
        return terminal.render(result)
    }
}

inline fun <reified T> T.renderOn(terminal: Terminal, newline: Boolean = true) {
    val message = SerializableRender(serializer<T>()).render(this, terminal)
    terminal.printNewOrNoLine(message, newline)
}