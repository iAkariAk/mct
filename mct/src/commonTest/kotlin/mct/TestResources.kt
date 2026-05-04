package mct

import arrow.core.getOrElse
import arrow.core.raise.either
import com.goncalossilva.resources.Resource
import korlibs.io.lang.unreachable
import mct.util.io.openZipReadWrite
import mct.util.io.useAsync
import okio.Path.Companion.toPath
import kotlin.properties.ReadOnlyProperty

private val test_map = Resource("TestMap.zip")

suspend fun TestMapWorkspace() = test_map.readBytes().openZipReadWrite().useAsync {
    val env = Env(
        fs = it,
        logger = Logger.Console()
    )
    either {
        MCTWorkspace("MCT Test".toPath(), env)
    }.getOrElse { unreachable }
}


object TestFunctions {
    private fun mcf(name: String? = null) = ReadOnlyProperty<Any, String> { thisRef, property ->
        val name = name ?: property.name
        Resource("mcfunctions/$name.mcfunction").readText()
    }

    val update_billboard by mcf()
}