package com.github.ohosrs.oxkintellijplugin.lsp

import com.github.ohosrs.oxkintellijplugin.ProcessCommandParameter
import com.github.ohosrs.oxkintellijplugin.buildOxkCommand
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.net.URI
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.PublishDiagnosticsCapabilities
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceClientCapabilities
import org.wso2.lsp4intellij.client.languageserver.serverdefinition.RawCommandServerDefinition

class OxkLintServerDefinition(
    private val project: Project,
    private val root: VirtualFile,
    executable: String,
    parameters: List<ProcessCommandParameter>,
    extension: String,
) : RawCommandServerDefinition(
    normalizeLspExtension(extension),
    languageIds(listOf(extension)),
    buildOxkCommand(executable, parameters),
) {

    override fun getInitializationOptions(rootUri: URI): Any {
        val initializationOptions = createOxkInitializationOptions(
            project,
            root.toNioPath().toUri().toString().removeSuffix("/"),
        )
        thisLogger().debug("Oxk lint initialization options: $initializationOptions")
        return initializationOptions
    }

    override fun customizeInitializeParams(params: InitializeParams) {
        configureOxkClientCapabilities(params)
        thisLogger().debug("Oxk lint initialize params: $params")
    }
}

internal fun configureOxkClientCapabilities(params: InitializeParams) {
    val capabilities = params.capabilities ?: ClientCapabilities().also { params.capabilities = it }
    val workspace = capabilities.workspace ?: WorkspaceClientCapabilities().also {
        capabilities.workspace = it
    }
    workspace.configuration = true

    val textDocument = capabilities.textDocument ?: TextDocumentClientCapabilities().also {
        capabilities.textDocument = it
    }
    val publishDiagnostics = textDocument.publishDiagnostics ?: PublishDiagnosticsCapabilities().also {
        textDocument.publishDiagnostics = it
    }
    publishDiagnostics.versionSupport = true
}

internal fun normalizeLspExtension(extension: String): String =
    extension.trim().removePrefix(".")

private fun languageIds(extensions: List<String>): Map<String, String> =
    extensions.mapNotNull { extension ->
        val key = normalizeLspExtension(extension).takeIf(String::isNotBlank) ?: return@mapNotNull null
        key to languageIdForOxkExtension(key)
    }.toMap()
