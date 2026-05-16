package com.github.ohosrs.oxkintellijplugin

import com.intellij.execution.ExecutionException
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import kotlin.io.path.Path

fun runOxkCommandOnFile(
    project: Project,
    file: VirtualFile,
    document: Document,
    executable: String,
    parameters: List<ProcessCommandParameter>,
    commandName: String,
    allowNonZeroExit: Boolean = false,
) {
    val manager = FileDocumentManager.getInstance()
    val saveDocument = {
        manager.saveDocument(document)
    }
    if (ApplicationManager.getApplication().isDispatchThread) {
        saveDocument()
    } else {
        ApplicationManager.getApplication().invokeAndWait(saveDocument)
    }

    val (workingDirectory, filePath) = ReadAction.compute<Pair<String?, String>, Throwable> {
        val workingDirectory = ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(file)?.path
            ?: project.basePath
            ?: file.parent?.path
        workingDirectory to file.path
    }

    val targetRun = OxkTargetRunBuilder(project)
        .getBuilder(ConfigurationMode.MANUAL, executable)
        .setWorkingDirectory(workingDirectory)
        .addParameters(parameters)
        .addParameters(listOf(ProcessCommandParameter.FilePath(Path(filePath))))
        .build()

    val handler = targetRun.startProcess()
    val output = (handler as? CapturingProcessHandler)?.runProcess(10_000)
        ?: throw ExecutionException("Unable to capture Oxk process output.")

    if (output.isTimeout) {
        throw ExecutionException("Oxk command timed out.")
    }

    if (!allowNonZeroExit && output.exitCode != 0) {
        val message = output.stderr.ifBlank { output.stdout }.ifBlank { "Oxk exited with ${output.exitCode}." }
        throw ExecutionException(message)
    }

    file.refresh(false, false)
    val updatedText = ReadAction.compute<String?, Throwable> {
        runCatching { file.readText() }.getOrNull()
    } ?: return
    val currentText = ReadAction.compute<String, Throwable> { document.text }
    if (currentText == updatedText) {
        return
    }

    val writeDocument = {
        WriteCommandAction.runWriteCommandAction(project, commandName, NOTIFICATION_GROUP, {
            document.setText(updatedText)
        })
    }
    if (ApplicationManager.getApplication().isDispatchThread) {
        writeDocument()
    } else {
        ApplicationManager.getApplication().invokeAndWait(writeDocument)
    }
}
