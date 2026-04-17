package mct.util.formatir

import mct.util.BuilderMaker

@BuilderMaker
interface IRObjectBuilder {
    fun put(key: String, value: IRElement): IRElement
}

fun buildIRObject(builderAction: IRObjectBuilder.() -> Unit): IRObject {
    val obj = mutableMapOf<String, IRElement>()
    val scope = object : IRObjectBuilder {
        override fun put(key: String, value: IRElement): IRElement {
            obj[key] = value
            return value
        }
    }
    scope.apply(builderAction)
    return IRObject(obj)
}

@BuilderMaker
interface IRListBuilder {
    fun add(value: IRElement): IRElement
}

fun buildIRList(builderAction: IRListBuilder.() -> Unit): IRList {
    val list = mutableListOf<IRElement>()
    val scope = object : IRListBuilder {
        override fun add(value: IRElement): IRElement {
            list.add(value)
            return value
        }
    }
    scope.apply(builderAction)
    return IRList(list)
}


inline fun IRObjectBuilder.put(key: String, value: Boolean) = put(key, IRBoolean(value))
inline fun IRObjectBuilder.put(key: String, value: Byte) = put(key, IRByte(value))
inline fun IRObjectBuilder.put(key: String, value: Short) = put(key, IRShort(value))
inline fun IRObjectBuilder.put(key: String, value: Int) = put(key, IRInt(value))
inline fun IRObjectBuilder.put(key: String, value: Long) = put(key, IRLong(value))
inline fun IRObjectBuilder.put(key: String, value: Float) = put(key, IRFloat(value))
inline fun IRObjectBuilder.put(key: String, value: Double) = put(key, IRDouble(value))
inline fun IRObjectBuilder.put(key: String, value: String) = put(key, IRString(value))

inline fun IRObjectBuilder.putIfPresent(key: String, value: IRElement?) = value?.let { put(key, value) }
inline fun IRObjectBuilder.putIfPresent(key: String, value: Boolean?) = putIfPresent(key, value?.let(::IRBoolean))
inline fun IRObjectBuilder.putIfPresent(key: String, value: Byte?) = putIfPresent(key, value?.let(::IRByte))
inline fun IRObjectBuilder.putIfPresent(key: String, value: Short?) = putIfPresent(key, value?.let(::IRShort))
inline fun IRObjectBuilder.putIfPresent(key: String, value: Int?) = putIfPresent(key, value?.let(::IRInt))
inline fun IRObjectBuilder.putIfPresent(key: String, value: Long?) = putIfPresent(key, value?.let(::IRLong))
inline fun IRObjectBuilder.putIfPresent(key: String, value: Float?) = putIfPresent(key, value?.let(::IRFloat))
inline fun IRObjectBuilder.putIfPresent(key: String, value: Double?) = putIfPresent(key, value?.let(::IRDouble))
inline fun IRObjectBuilder.putIfPresent(key: String, value: String?) = putIfPresent(key, value?.let(::IRString))
