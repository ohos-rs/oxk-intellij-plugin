package com.github.ohosrs.oxkintellijplugin

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

private val LOG = Logger.getInstance("#com.github.ohosrs.oxkintellijplugin.OxkExecutable")

fun findConfiguredOxkExecutable(): String? {
    val configuredPath = System.getenv(OXK_BINARY_PATH_ENV)?.takeIf { it.isNotBlank() } ?: return null
    val executable = Paths.get(configuredPath).toAbsolutePath().normalize()
    return executable.toString().takeIf { isRunnableFile(executable) }
}

fun findProjectOxkExecutable(virtualFile: VirtualFile): String? {
    val start = virtualFile.toNioPath().let { if (Files.isDirectory(it)) it else it.parent } ?: return null

    return findOxkExecutableFrom(start)
}

fun findProjectOxkLintLspExecutable(virtualFile: VirtualFile): String? {
    val start = virtualFile.toNioPath().let { if (Files.isDirectory(it)) it else it.parent } ?: return null

    return findOxkLintLspExecutableFrom(start)
}

fun findOxkExecutableFrom(start: Path): String? {
    return findOxkExecutableCandidatesFrom(start)
        .firstOrNull(::isRunnableFile)
        ?.toAbsolutePath()
        ?.normalize()
        ?.toString()
}

fun findOxkLintLspExecutableFrom(start: Path): String? {
    return findOxkExecutableCandidatesFrom(start)
        .firstOrNull { isRunnableFile(it) && supportsOxkLintLsp(it.toAbsolutePath().normalize().toString()) }
        ?.toAbsolutePath()
        ?.normalize()
        ?.toString()
}

fun isNodeScript(path: String): Boolean {
    val executable = Paths.get(path)
    return runCatching {
        Files.isRegularFile(executable) &&
            Files.newBufferedReader(executable).use { it.readLine() }?.contains("node") == true
    }.getOrDefault(false)
}

const val OXK_BINARY_PATH_ENV = "OXK_BINARY_PATH"

private val LINT_LSP_SUPPORT_CACHE = ConcurrentHashMap<String, Boolean>()

private val OXK_RELATIVE_EXECUTABLES = listOf(
    Paths.get("target", "debug", "oxk"),
    Paths.get("target", "release", "oxk"),
    Paths.get("oxc-ark", "target", "debug", "oxk"),
    Paths.get("oxc-ark", "target", "release", "oxk"),
    Paths.get("node_modules", ".bin", "oxk"),
    Paths.get("node_modules", ".bin", "oxk.cmd"),
    Paths.get("node_modules", "@ohos-rs", "oxk", "bin", "oxk.js"),
)

private fun findOxkExecutableCandidatesFrom(start: Path): Sequence<Path> {
    val projectCandidates = generateSequence(start.toAbsolutePath().normalize()) { it.parent }
        .flatMap { directory ->
            OXK_RELATIVE_EXECUTABLES.asSequence().map { directory.resolve(it) }
        }

    return (projectCandidates + findCommonOxkExecutableCandidates().asSequence()).distinct()
}

private fun findCommonOxkExecutableCandidates(): List<Path> {
    val home = System.getProperty("user.home")?.let(Paths::get)
    val pathCandidates = System.getenv("PATH")
        .orEmpty()
        .split(File.pathSeparator)
        .filter(String::isNotBlank)
        .map { Paths.get(it).resolve("oxk") }

    return pathCandidates + listOfNotNull(
        home?.resolve(Paths.get("Library", "pnpm", "oxk")),
        home?.resolve(Paths.get(".local", "share", "pnpm", "oxk")),
        home?.resolve(Paths.get(".npm-global", "bin", "oxk")),
        home?.resolve(Paths.get("node_modules", ".bin", "oxk")),
        Paths.get("/opt/homebrew/bin/oxk"),
        Paths.get("/usr/local/bin/oxk"),
        home?.resolve(Paths.get(".cargo", "bin", "oxk")),
    )
}

fun supportsOxkLintLsp(executable: String): Boolean {
    return LINT_LSP_SUPPORT_CACHE.computeIfAbsent(executable) {
        probeOxkLintLsp(executable).also { supported ->
            if (!supported) {
                LOG.warn("Oxk executable did not respond to lint LSP probe: $executable")
            }
        }
    }
}

private fun probeOxkLintLsp(executable: String): Boolean {
    val parameters = listOf(
        ProcessCommandParameter.Value("lint"),
        ProcessCommandParameter.Value("--lsp"),
    )
    val command = runCatching { buildOxkCommand(executable, parameters) }
        .onFailure { LOG.warn("Failed to build Oxk lint LSP probe command for $executable", it) }
        .getOrElse { return false }

    return runCatching {
        val requestBody =
            """{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"processId":null,"rootUri":"file:///tmp","capabilities":{}}}"""
        val request = "Content-Length: ${requestBody.toByteArray(StandardCharsets.UTF_8).size}\r\n\r\n$requestBody"
        val process = ProcessBuilder(command.toList()).start()
        val stdout = StringBuffer()
        val stderr = StringBuffer()
        val stdoutReader = thread(start = true, isDaemon = true, name = "oxk-lsp-probe-stdout") {
            drainProcessStream(process.inputStream, stdout)
        }
        val stderrReader = thread(start = true, isDaemon = true, name = "oxk-lsp-probe-stderr") {
            drainProcessStream(process.errorStream, stderr)
        }

        var supported = false
        try {
            process.outputStream.write(request.toByteArray(StandardCharsets.UTF_8))
            process.outputStream.flush()

            supported = waitForLspProbeResult(process, stdout)
            supported
        } finally {
            runCatching { process.outputStream.close() }
            if (process.isAlive) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
            }
            stdoutReader.join(200)
            stderrReader.join(200)
            if (!supported) {
                LOG.warn(
                    "Oxk lint LSP probe failed: command=${command.joinToString(" ")} " +
                        "stdout=${stdout.toLogSnippet()} stderr=${stderr.toLogSnippet()}",
                )
            }
        }
    }.onFailure {
        LOG.warn("Oxk lint LSP probe failed to start: command=${command.joinToString(" ")}", it)
    }.getOrDefault(false)
}

private fun isRunnableFile(path: Path): Boolean =
    Files.isRegularFile(path) &&
        (Files.isExecutable(path) || path.toString().endsWith(".cmd") || isNodeScript(path.toString()))

private fun waitForLspProbeResult(process: Process, stdout: StringBuffer): Boolean {
    val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OXK_LINT_LSP_PROBE_TIMEOUT_MS)
    while (System.nanoTime() < deadline) {
        if (hasInitializeCapabilities(stdout)) {
            return true
        }
        if (!process.isAlive) {
            process.waitFor(100, TimeUnit.MILLISECONDS)
            return hasInitializeCapabilities(stdout)
        }
        Thread.sleep(50)
    }
    return hasInitializeCapabilities(stdout)
}

private fun hasInitializeCapabilities(stdout: StringBuffer): Boolean =
    stdout.toString().let {
        it.contains("\"result\"") && it.contains("\"capabilities\"")
    }

private fun drainProcessStream(inputStream: InputStream, output: StringBuffer) {
    val buffer = ByteArray(1024)
    while (true) {
        val read = runCatching { inputStream.read(buffer) }.getOrDefault(-1)
        if (read < 0) {
            break
        }
        output.append(String(buffer, 0, read, StandardCharsets.UTF_8))
    }
}

private fun StringBuffer.toLogSnippet(): String =
    synchronized(this) {
        toString()
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .take(1_000)
    }

private const val OXK_LINT_LSP_PROBE_TIMEOUT_MS = 10_000L
