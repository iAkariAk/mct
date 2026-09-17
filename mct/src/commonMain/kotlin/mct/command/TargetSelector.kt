package mct.command

import mct.util.Regex2
import mct.util.StringIndices
import mct.util.groups2

private val TARGET_SELECTOR_REGEX = Regex2("""^@[praesn]\[.*]$""")
private val TARGET_SELECTOR_NAME_REGEX = Regex2("""name=!?("(?:\\.|.)*?"|'.*?'|[\w:]*)[,\]]""")

fun String.isTargetSelector() = TARGET_SELECTOR_REGEX.matches(this)
fun String.getTargetSelectorName(): StringIndices? {
    val result = TARGET_SELECTOR_NAME_REGEX.find(this)
    val name = result?.groups2[1] ?: return null
    return StringIndices(name.range, name.value)
}