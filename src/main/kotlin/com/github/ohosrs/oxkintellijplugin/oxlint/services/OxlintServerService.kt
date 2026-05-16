package com.github.ohosrs.oxkintellijplugin.oxlint.services

import com.github.ohosrs.oxkintellijplugin.NOTIFICATION_GROUP
import com.github.ohosrs.oxkintellijplugin.ProcessCommandParameter
import com.github.ohosrs.oxkintellijplugin.lsp.OxkLspServerService
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintBundle
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintFixKind
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintPackage
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintUnusedDisableDirectivesSeverity
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.github.ohosrs.oxkintellijplugin.notifyOxkExecutableNotFound
import com.github.ohosrs.oxkintellijplugin.runOxkCommandOnFile
import com.intellij.execution.ExecutionException
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class OxlintServerService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): OxlintServerService = project.getService(OxlintServerService::class.java)
    }

    suspend fun fixAll(document: Document) {
        val manager = FileDocumentManager.getInstance()
        val file = manager.getFile(document) ?: return

        fixAll(file, document)
    }

    suspend fun fixAll(file: VirtualFile, document: Document) {
        val settings = OxlintSettings.getInstance(project)
        val fixFlag = settings.fixKind.toCliFixFlag() ?: return
        val oxlint = OxlintPackage(project)
        val executable = oxlint.binaryPath(file)
        if (executable == null) {
            notifyOxkExecutableNotFound(project)
            throw ExecutionException("Oxk executable not found.")
        }

        runOxkCommandOnFile(
            project,
            file,
            document,
            executable,
            createFixParameters(oxlint, settings, fixFlag),
            OxlintBundle.message("oxlint.run.quickfix"),
            allowNonZeroExit = true,
        )
    }

    fun restartServer() {
        OxkLspServerService.getInstance(project).restart()
    }

    fun stopServer() {
        OxkLspServerService.getInstance(project).stop()
    }

    fun notifyRestart() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(
                OxlintBundle.message("oxlint.language.server.restarted"),
                "",
                NotificationType.INFORMATION
            )
            .notify(project)
    }

    private fun createFixParameters(
        oxlint: OxlintPackage,
        settings: OxlintSettings,
        fixFlag: String,
    ): List<ProcessCommandParameter> {
        val parameters = mutableListOf<ProcessCommandParameter>(
            ProcessCommandParameter.Value("lint"),
            ProcessCommandParameter.Value(fixFlag),
        )

        oxlint.configPath()?.takeIf(String::isNotBlank)?.let {
            parameters.add(ProcessCommandParameter.Value("--config=$it"))
        }
        if (settings.disableNestedConfig) {
            parameters.add(ProcessCommandParameter.Value("--disable-nested-config"))
        }
        if (settings.typeAware) {
            parameters.add(ProcessCommandParameter.Value("--type-aware"))
        }
        when (settings.unusedDisableDirectivesSeverity) {
            OxlintUnusedDisableDirectivesSeverity.ALLOW -> Unit
            OxlintUnusedDisableDirectivesSeverity.WARN ->
                parameters.add(ProcessCommandParameter.Value("--report-unused-disable-directives-severity=warn"))
            OxlintUnusedDisableDirectivesSeverity.DENY ->
                parameters.add(ProcessCommandParameter.Value("--report-unused-disable-directives-severity=deny"))
        }

        return parameters
    }

    private fun OxlintFixKind.toCliFixFlag(): String? =
        when (this) {
            OxlintFixKind.SAFE_FIX -> "--fix"
            OxlintFixKind.SAFE_FIX_OR_SUGGESTION -> "--fix-suggestions"
            OxlintFixKind.DANGEROUS_FIX,
            OxlintFixKind.DANGEROUS_FIX_OR_SUGGESTION,
            OxlintFixKind.ALL -> "--fix-dangerously"
            OxlintFixKind.NONE -> null
        }
}
