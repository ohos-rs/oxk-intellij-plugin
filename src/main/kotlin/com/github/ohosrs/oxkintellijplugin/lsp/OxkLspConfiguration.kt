package com.github.ohosrs.oxkintellijplugin.lsp

import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintPackage
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.openapi.project.Project

internal fun createOxkInitializationOptions(
    project: Project,
    workspaceUri: String,
): List<Map<String, Any?>> =
    listOf(
        mapOf(
            "workspaceUri" to workspaceUri.removeSuffix("/"),
            "options" to createOxkWorkspaceConfig(project),
        )
    )

internal fun createOxkWorkspaceConfig(project: Project): Map<String, Any?> {
    val oxlintPackage = OxlintPackage(project)
    val settings = OxlintSettings.getInstance(project)

    return mapOf(
        "configPath" to oxlintPackage.configPath(),
        "disableNestedConfig" to settings.disableNestedConfig,
        "fixKind" to settings.fixKind.toLspValue(),
        "flags" to mapOf(
            "disable_nested_config" to settings.disableNestedConfig.toString(),
            "fix_kind" to settings.fixKind.toLspValue(),
        ),
        "run" to settings.state.runTrigger.toLspValue(),
        "typeAware" to settings.typeAware,
        "unusedDisableDirectives" to settings.state.unusedDisableDirectives.toLspValue(),
    )
}

internal fun languageIdForOxkExtension(extension: String): String =
    when (normalizeLspExtension(extension)) {
        "js", "cjs", "mjs" -> "javascript"
        "jsx" -> "javascriptreact"
        "ts", "cts", "mts", "ets" -> "typescript"
        "tsx" -> "typescriptreact"
        else -> normalizeLspExtension(extension)
    }
