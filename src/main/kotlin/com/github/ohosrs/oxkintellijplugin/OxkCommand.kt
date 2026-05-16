package com.github.ohosrs.oxkintellijplugin

import com.intellij.openapi.application.PathManager
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

fun buildOxkCommand(
    executable: String,
    parameters: List<ProcessCommandParameter>,
): Array<String> {
    val arguments = parameters.map { it.toString() }
    val nodeScript = findWrappedNodeScript(executable) ?: executable.takeIf(::isNodeScript)
    return if (nodeScript != null) {
        (listOf(findNodeExecutable(), nodeScript) + arguments).toTypedArray()
    } else {
        (listOf(executable) + arguments).toTypedArray()
    }
}

private fun findWrappedNodeScript(executable: String): String? {
    val path = Paths.get(executable)
    return runCatching {
        if (!Files.isRegularFile(path)) {
            return null
        }
        val wrapper = Files.newBufferedReader(path).use { reader ->
            generateSequence { reader.readLine() }
                .take(80)
                .joinToString("\n")
        }
        WRAPPED_NODE_SCRIPT_REGEX.find(wrapper)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { path.parent.resolve(it).normalize() }
            ?.takeIf(Files::isRegularFile)
            ?.toString()
    }.getOrNull()
}

private fun findNodeExecutable(): String {
    val ideHome = Paths.get(PathManager.getHomePath())
    val bundledNodeCandidates = listOf(
        ideHome.resolve(Paths.get("tools", "node", "bin", "node")),
        ideHome.resolve(Paths.get("tools", "node", "node.exe")),
    )
    bundledNodeCandidates.firstOrNull(Files::isExecutable)?.let {
        return it.toString()
    }

    return System.getenv("PATH")
        .orEmpty()
        .split(File.pathSeparator)
        .filter(String::isNotBlank)
        .map(Paths::get)
        .flatMap { listOf(it.resolve("node"), it.resolve("node.exe")) }
        .firstOrNull(Files::isExecutable)
        ?.toString()
        ?: "node"
}

private val WRAPPED_NODE_SCRIPT_REGEX = Regex("""\${'$'}\{?basedir}?/([^"']+\.js)""")
