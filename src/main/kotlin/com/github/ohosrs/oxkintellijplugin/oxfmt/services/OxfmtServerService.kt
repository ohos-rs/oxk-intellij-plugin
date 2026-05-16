package com.github.ohosrs.oxkintellijplugin.oxfmt.services

import com.github.ohosrs.oxkintellijplugin.NOTIFICATION_GROUP
import com.github.ohosrs.oxkintellijplugin.ProcessCommandParameter
import com.github.ohosrs.oxkintellijplugin.oxfmt.OxfmtBundle
import com.github.ohosrs.oxkintellijplugin.oxfmt.OxfmtPackage
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
class OxfmtServerService(private val project: Project) {

    companion object {

        fun getInstance(project: Project): OxfmtServerService =
            project.getService(OxfmtServerService::class.java)
    }

    suspend fun fixAll(document: Document) {
        val manager = FileDocumentManager.getInstance()
        val file = manager.getFile(document) ?: return

        fixAll(file, document)
    }

    suspend fun fixAll(file: VirtualFile, document: Document) {
        val oxfmt = OxfmtPackage(project)
        val executable = oxfmt.binaryPath(file)
        if (executable == null) {
            notifyOxkExecutableNotFound(project)
            throw ExecutionException("Oxk executable not found.")
        }
        runOxkCommandOnFile(
            project,
            file,
            document,
            executable,
            createFormatParameters(oxfmt),
            OxfmtBundle.message("oxfmt.run.quickfix"),
        )
    }

    fun restartServer() {
        // Formatting runs through the local Oxk CLI on DevEco. No separate LSP server is needed.
    }

    fun stopServer() {
        // Formatting runs through the local Oxk CLI on DevEco. No separate LSP server is needed.
    }

    fun notifyRestart() {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
            .createNotification(OxfmtBundle.message("oxfmt.language.server.restarted"), "",
                NotificationType.INFORMATION).notify(project)
    }

    private fun createFormatParameters(oxfmt: OxfmtPackage): List<ProcessCommandParameter> {
        val parameters = mutableListOf<ProcessCommandParameter>(
            ProcessCommandParameter.Value("format"),
        )
        oxfmt.configPath()?.takeIf(String::isNotBlank)?.let {
            parameters.add(ProcessCommandParameter.Value("--config=$it"))
        }
        return parameters
    }
}
