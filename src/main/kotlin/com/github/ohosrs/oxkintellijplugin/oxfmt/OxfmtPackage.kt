package com.github.ohosrs.oxkintellijplugin.oxfmt

import com.github.ohosrs.oxkintellijplugin.ConfigurationMode
import com.github.ohosrs.oxkintellijplugin.ProcessCommandParameter
import com.github.ohosrs.oxkintellijplugin.findConfiguredOxkExecutable
import com.github.ohosrs.oxkintellijplugin.findProjectOxkExecutable
import com.github.ohosrs.oxkintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class OxfmtPackage(private val project: Project) {
    fun configPath(): String? {
        val settings = OxfmtSettings.getInstance(project)
        val configurationMode = settings.configurationMode
        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> null
            ConfigurationMode.MANUAL -> settings.configPath
        }
    }

    fun binaryPath(
        virtualFile: VirtualFile,
    ): String? {
        val settings = OxfmtSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> findOxkExecutable(virtualFile)
            ConfigurationMode.MANUAL -> settings.binaryPath.ifBlank {
                findOxkExecutable(virtualFile)
            }
        }
    }

    fun binaryParameters(@Suppress("UNUSED_PARAMETER") virtualFile: VirtualFile): List<ProcessCommandParameter> {
        return findOxkLspParameters()
    }

    fun isEnabled(): Boolean {
        val settings = OxfmtSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxkExecutable(virtualFile: VirtualFile): String? {
        findConfiguredOxkExecutable()?.let { return it }
        return findProjectOxkExecutable(virtualFile)
    }

    private fun findOxkLspParameters(): List<ProcessCommandParameter> =
        OXK_FORMAT_LSP_COMMAND.map { ProcessCommandParameter.Value(it) }

    companion object {
        const val CONFIG_NAME = ".oxfmtrc"
        const val CONFIG_TS_NAME = "oxfmt.config.ts"
        val OXK_FORMAT_LSP_COMMAND = listOf("format", "--lsp")
        val CONFIG_VALID_JSON_EXTENSIONS = listOf("json", "jsonc")
    }
}
