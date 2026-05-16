package com.github.ohosrs.oxkintellijplugin

import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintBundle
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.wsl.WSLDistribution
import com.intellij.execution.wsl.WslPath
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.util.io.BaseOutputReader
import kotlin.io.path.Path


fun wrapStartProcess(processCreator: () -> OSProcessHandler): OSProcessHandler =
    ProgressManager.getInstance().runProcess(Computable(processCreator), EmptyProgressIndicator())

sealed interface OxkTargetRun {
    fun startProcess(): OSProcessHandler
    fun toTargetPath(path: String): String
    fun toLocalPath(path: String): String

    class General(
        private val command: GeneralCommandLine,
        private val wslDistribution: WSLDistribution? = null,
    ) : OxkTargetRun {
        override fun startProcess(): OSProcessHandler =
            wrapStartProcess {
                val logger = Logger.getInstance("#com.github.ohosrs.oxkintellijplugin")
                val level = if (logger.isTraceEnabled) "TRACE" else if (logger.isDebugEnabled) "DEBUG" else "INFO"
                command.environment.putIfAbsent("OXK_LOG", level)
                command.environment.putIfAbsent("RUST_LOG", level)

                object : CapturingProcessHandler(command) {
                    override fun readerOptions(): BaseOutputReader.Options {
                        return object : BaseOutputReader.Options() {
                            override fun splitToLines(): Boolean = false
                        }
                    }
                }
            }

        override fun toTargetPath(path: String) = wslDistribution?.getWslPath(Path(path)) ?: path
        override fun toLocalPath(path: String) = wslDistribution?.getWindowsPath(path) ?: path
    }
}

class OxkTargetRunBuilder(val project: Project) {
    fun getBuilder(
        configMode: ConfigurationMode,
        executable: String,
    ): ProcessCommandBuilder {
        if (executable.isEmpty()) {
            throw ExecutionException(OxlintBundle.message("oxlint.language.server.not.found"))
        }

        val wslPath = WslPath.parseWindowsUncPath(executable)
        val builder = GeneralProcessCommandBuilder()

        if (wslPath == null) {
            val command = buildOxkCommand(executable, emptyList())
            if (command.firstOrNull() != executable) {
                return builder
                    .setExecutable(command.first())
                    .addParameters(command.drop(1).map { ProcessCommandParameter.Value(it) })
                    .setCharset(Charsets.UTF_8)
            }
        }

        return builder.setExecutable(executable).setCharset(Charsets.UTF_8)
    }
}
