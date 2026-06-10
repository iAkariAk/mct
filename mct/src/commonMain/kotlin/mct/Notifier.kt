package mct

import kotlinx.serialization.serializer
import mct.util.BuilderMaker
import kotlin.reflect.KClass

inline fun KClass<*>.identifier() = qualifiedName ?: toString()

fun interface Notifier {
    fun notify(key: String, value: Any?)

    companion object {
        val None = Notifier { }
    }
}

inline fun <reified T> Notifier.notify(message: () -> T) {
    val key = T::class.identifier()
    notify(key, message())
}

data class RegistryItem<T : Any>(
    val clazz: KClass<T>,
    val callback: (T) -> Unit,
) {
    @Suppress("UNCHECKED_CAST")
    operator fun invoke(data: Any) = callback(data as T)

    val key = clazz.identifier()

    val serializer = clazz.serializer()
}

@BuilderMaker
interface OnSignRegistry {
    fun <T : Any> on(clazz: KClass<T>, callback: (T) -> Unit)
}

inline fun <reified T : Any> OnSignRegistry.on(noinline callback: (T) -> Unit) = on(T::class, callback)

inline fun Notifier(register: OnSignRegistry.() -> Unit): Notifier {
    val hooks = mutableMapOf<String, RegistryItem<*>>()

    val registry = object : OnSignRegistry {
        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> on(clazz: KClass<T>, callback: (T) -> Unit) {
            hooks[clazz.qualifiedName ?: clazz.toString()] = RegistryItem(clazz, callback as (Any) -> Unit)
        }
    }
    registry.register()
    return Notifier { key, value -> hooks[key]?.invoke(value!!) }
}

