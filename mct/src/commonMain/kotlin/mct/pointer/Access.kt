package mct.pointer

import arrow.core.raise.context.Raise
import arrow.core.raise.context.ensure
import arrow.core.raise.context.raise
import mct.MCTError
import mct.util.formatir.IRElement
import mct.util.formatir.IRList
import mct.util.formatir.IRObject
import mct.util.formatir.IRString

sealed interface AccessError : MCTError {
    data class IndexOutOfBound(val index: Int, val size: Int) : AccessError {
        override val message = "Try to access $index in a list of size $size"
    }

    data class KeyNotFound(val key: String, val keys: Set<String>) : AccessError {
        override val message = "Try to access $key in a map of ${keys.joinToString(prefix = "{", postfix = "}")}"
    }

    data class TypeNotMatch(val expected: String, val actual: String) : AccessError {
        override val message = "Expected $expected but got $actual"
    }
}

context(_: Raise<AccessError>)
private inline fun <reified T> IRElement.shouldBe(): T {
    ensure(this is T) {
        AccessError.TypeNotMatch(T::class.simpleName!!, this::class.simpleName!!)
    }
    return this
}

context(_: Raise<AccessError>)
fun IRElement.access(pointer: DataPointer): String = when (pointer) {
    is DataPointer.List -> {
        val ir = shouldBe<IRList>()

        (ir.getOrNull(pointer.point) ?: raise(AccessError.IndexOutOfBound(pointer.point, ir.size)))
            .access(pointer.value)
    }

    is DataPointer.Map -> {
        val ir = shouldBe<IRObject>()

        (ir.value[pointer.point] ?: raise(AccessError.KeyNotFound(pointer.point, ir.keys)))
            .access(pointer.value)
    }

    DataPointer.Terminator -> {
        val ir = shouldBe<IRString>()

        ir.value
    }
}
