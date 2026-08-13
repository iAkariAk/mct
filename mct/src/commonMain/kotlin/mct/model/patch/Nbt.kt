package mct.model.patch

import kotlinx.serialization.Serializable
import mct.pointer.DataPointer

/**
 *  @property pointer The NBT path/pointer to the specific tag
 */
@Serializable
data class NbtExtraction(
    val pointer: DataPointer,
    val content: ExtractionContent
) {

    inline fun replace(replace: (ExtractionContent) -> ReplacementContent) = NbtReplacement(pointer, replace(content))

}

/**
 *  @property pointer The NBT path/pointer identifying the tag to replace
 */
@Serializable
data class NbtReplacement(
    val pointer: DataPointer,
    val content: ReplacementContent
) : Replacement