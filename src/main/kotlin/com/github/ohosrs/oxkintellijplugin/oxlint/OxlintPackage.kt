package com.github.ohosrs.oxkintellijplugin.oxlint

import com.github.ohosrs.oxkintellijplugin.ConfigurationMode
import com.github.ohosrs.oxkintellijplugin.ProcessCommandParameter
import com.github.ohosrs.oxkintellijplugin.findConfiguredOxkExecutable
import com.github.ohosrs.oxkintellijplugin.findProjectOxkLintLspExecutable
import com.github.ohosrs.oxkintellijplugin.findProjectOxkExecutable
import com.github.ohosrs.oxkintellijplugin.supportsOxkLintLsp
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class OxlintPackage(private val project: Project) {
    fun configPath(): String? {
        val settings = OxlintSettings.getInstance(project)
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
        val settings = OxlintSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> findOxkExecutable(virtualFile)
            ConfigurationMode.MANUAL -> settings.binaryPath.ifBlank {
                findOxkExecutable(virtualFile)
            }
        }
    }

    fun lspBinaryPath(
        virtualFile: VirtualFile,
    ): String? {
        val settings = OxlintSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        return when (configurationMode) {
            ConfigurationMode.DISABLED -> null
            ConfigurationMode.AUTOMATIC -> findOxkLintLspExecutable(virtualFile)
            ConfigurationMode.MANUAL -> {
                settings.binaryPath.takeIf(String::isNotBlank)
                    ?: findOxkLintLspExecutable(virtualFile)
            }
        }
    }

    fun binaryParameters(virtualFile: VirtualFile): List<ProcessCommandParameter> {
        val settings = OxlintSettings.getInstance(project)
        val configurationMode = settings.configurationMode

        return when (configurationMode) {
            ConfigurationMode.DISABLED -> emptyList()
            ConfigurationMode.AUTOMATIC -> {
                findOxkLspParameters()
            }
            ConfigurationMode.MANUAL -> {
                if (settings.binaryPath.isBlank()) {
                    findOxkLspParameters()
                } else {
                    val parameters: List<String> = settings.binaryParameters
                        .takeUnless { it.isEmpty() || it == listOf(LEGACY_OXK_LSP_COMMAND) }
                        ?: OXK_LINT_LSP_COMMAND
                    parameters.map { ProcessCommandParameter.Value(it) }
                }
            }
        }
    }

    fun isEnabled(): Boolean {
        val settings = OxlintSettings.getInstance(project)
        return settings.configurationMode != ConfigurationMode.DISABLED
    }

    private fun findOxkExecutable(virtualFile: VirtualFile): String? {
        findConfiguredOxkExecutable()?.let { return it }
        return findProjectOxkExecutable(virtualFile)
    }

    private fun findOxkLintLspExecutable(virtualFile: VirtualFile): String? {
        findConfiguredOxkExecutable()?.takeIf(::supportsOxkLintLsp)?.let { return it }
        return findProjectOxkLintLspExecutable(virtualFile)
    }

    private fun findOxkLspParameters(): List<ProcessCommandParameter> =
        OXK_LINT_LSP_COMMAND.map { ProcessCommandParameter.Value(it) }

    companion object {
        const val CONFIG_NAME = ".oxlintrc"
        const val CONFIG_TS_NAME = "oxlint.config.ts"
        const val LEGACY_OXK_LSP_COMMAND = "lsp"
        val OXK_LINT_LSP_COMMAND = listOf("lint", "--lsp")
        val configValidJsonExtensions = listOf("json", "jsonc")
    }
}
