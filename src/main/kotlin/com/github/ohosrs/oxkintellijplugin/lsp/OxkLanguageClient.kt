package com.github.ohosrs.oxkintellijplugin.lsp

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import java.net.URI
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient

class OxkLanguageClient(
    private val project: Project,
    private val rootUri: String,
    private val diagnosticsStore: OxkLspDiagnosticStore,
) : LanguageClient {
    override fun publishDiagnostics(params: PublishDiagnosticsParams) {
        val uri = params.uri ?: return
        diagnosticsStore.update(uri, params.diagnostics.orEmpty())
        restartDaemon(uri)
    }

    override fun configuration(params: ConfigurationParams): CompletableFuture<List<Any>> {
        val config = createOxkWorkspaceConfig(project)
        return CompletableFuture.completedFuture(params.items.orEmpty().map { config })
    }

    override fun workspaceFolders(): CompletableFuture<List<WorkspaceFolder>> =
        CompletableFuture.completedFuture(listOf(WorkspaceFolder(rootUri, project.name)))

    override fun applyEdit(params: ApplyWorkspaceEditParams): CompletableFuture<ApplyWorkspaceEditResponse> =
        CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(false))

    override fun telemetryEvent(`object`: Any?) = Unit

    override fun showMessage(params: MessageParams) {
        if (project.isDisposed) {
            return
        }

        val message = params.message ?: return
        if (params.type == MessageType.Error) {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Oxk")
                .createNotification("Oxk language server", message, NotificationType.ERROR)
                .notify(project)
        } else {
            LOG.info("Oxk language server: $message")
        }
    }

    override fun showMessageRequest(params: ShowMessageRequestParams): CompletableFuture<MessageActionItem?> {
        showMessage(MessageParams(params.type, params.message))
        return CompletableFuture.completedFuture(null)
    }

    override fun logMessage(params: MessageParams) {
        val message = params.message ?: return
        when (params.type) {
            MessageType.Error -> LOG.warn("Oxk language server: $message")
            MessageType.Warning -> LOG.warn("Oxk language server: $message")
            else -> LOG.info("Oxk language server: $message")
        }
    }

    private fun restartDaemon(uri: String) {
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }

            val virtualFile = runCatching {
                VfsUtil.findFile(Paths.get(URI(uri)), true)
            }.getOrNull() ?: return@invokeLater

            ReadAction.run<RuntimeException> {
                PsiManager.getInstance(project).findFile(virtualFile)?.let {
                    com.intellij.codeInsight.daemon.DaemonCodeAnalyzer.getInstance(project).restart(it)
                }
            }
        }
    }

    companion object {
        private val LOG = Logger.getInstance("#com.github.ohosrs.oxkintellijplugin.lsp.OxkLanguageClient")
    }
}
