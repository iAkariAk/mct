package mct.util

import arrow.core.partially1
import kotlin.jvm.JvmInline

@JvmInline
value class NonLazyValue<T>(override val value: T) : Lazy<T> {
    override fun isInitialized() = true
}

inline fun <reified T> NonLazyNull() = object : Lazy<T?> {
    override val value = null
    override fun isInitialized() = true
}

class LazyList<T>(private val wrapper: List<Lazy<T>>) : List<T> {
    override val size get() = wrapper.size
    override fun isEmpty() = wrapper.isEmpty()

    override fun iterator(): Iterator<T> {
        val iter = wrapper.iterator()
        return object : Iterator<T> {
            override fun next() = iter.next().value
            override fun hasNext() = iter.hasNext()
        }
    }


    override fun listIterator(): ListIterator<T> {
        val iter = wrapper.listIterator()
        return object : ListIterator<T> {
            override fun nextIndex() = iter.nextIndex()
            override fun next() = iter.next().value
            override fun hasNext() = iter.hasNext()
            override fun previousIndex() = iter.previousIndex()
            override fun hasPrevious() = iter.hasPrevious()
            override fun previous() = iter.previous().value
        }
    }

    override fun listIterator(index: Int): ListIterator<T> {
        val iter = wrapper.listIterator(index)
        return object : ListIterator<T> {
            override fun nextIndex() = iter.nextIndex()
            override fun next() = iter.next().value
            override fun hasNext() = iter.hasNext()
            override fun previousIndex() = iter.previousIndex()
            override fun hasPrevious() = iter.hasPrevious()
            override fun previous() = iter.previous().value
        }
    }


    override fun containsAll(elements: Collection<T>) = elements.all { contains(it) }
    override fun contains(element: T) = wrapper.find { it.value == element } != null

    override fun get(index: Int) = wrapper[index].value

    override fun indexOf(element: T) = wrapper.indexOfFirst { it.value == element }

    override fun lastIndexOf(element: T) = wrapper.indexOfLast { it.value == element }

    override fun subList(fromIndex: Int, toIndex: Int) = LazyList(wrapper.subList(fromIndex, toIndex))
}

inline fun <T> List<Lazy<T>>.flatten(): LazyList<T> = LazyList(this)

inline fun <T> LazyList(size: Int, noinline init: (index: Int) -> T): LazyList<T> {
    val list = ArrayList<Lazy<T>>(size)
    repeat(size) { index -> list.add(lazy(init.partially1(index))) }
    return LazyList(list)
}
