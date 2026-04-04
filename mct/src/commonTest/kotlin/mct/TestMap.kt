package mct

import com.goncalossilva.resources.Resource
import mct.util.io.openZipReadWrite
import mct.util.io.useAsync
import okio.Path.Companion.toPath

private val resource = Resource("TestMap.zip")

suspend fun TestMapWorkspace() = resource.readBytes().openZipReadWrite().useAsync {
    val env = Env(
        fs = it,
        logger = Logger.Console()
    )
    MCTWorkspace("MCT Test".toPath(), env)
}
