package com.github.ohosrs.oxkintellijplugin.oxlint.inspections

import com.github.ohosrs.oxkintellijplugin.ConfigurationMode
import com.github.ohosrs.oxkintellijplugin.OxkTargetRunBuilder
import com.github.ohosrs.oxkintellijplugin.ProcessCommandParameter
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintPackage
import com.github.ohosrs.oxkintellijplugin.oxlint.annotations.OxlintDiagnostic
import com.github.ohosrs.oxkintellijplugin.oxlint.annotations.createOxlintLintParameters
import com.github.ohosrs.oxkintellijplugin.oxlint.annotations.parseOxlintJsonDiagnostics
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import kotlin.io.path.Path

class OxlintLocalInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = "Oxk lint"

    override fun getGroupDisplayName(): String = "Oxk"

    override fun runForWholeFile(): Boolean = true

    override fun isAvailableForFile(file: PsiFile): Boolean {
        val project = file.project.takeUnless { it.isDisposed } ?: return false
        val virtualFile = file.virtualFile ?: return false
        val settings = OxlintSettings.getInstance(project)
        return settings.isEnabled() && settings.fileSupported(virtualFile)
    }

    override fun checkFile(
        file: PsiFile,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): Array<ProblemDescriptor> {
        val project = file.project.takeUnless { it.isDisposed } ?: return ProblemDescriptor.EMPTY_ARRAY
        val virtualFile = file.virtualFile ?: return ProblemDescriptor.EMPTY_ARRAY
        val document = file.viewProvider.document ?: return ProblemDescriptor.EMPTY_ARRAY
        val settings = OxlintSettings.getInstance(project)
        if (!settings.isEnabled() || !settings.fileSupported(virtualFile)) {
            return ProblemDescriptor.EMPTY_ARRAY
        }

        val oxlint = OxlintPackage(project)
        val executable = oxlint.binaryPath(virtualFile) ?: return ProblemDescriptor.EMPTY_ARRAY
        val diagnostics = runOxlint(project, virtualFile, oxlint, settings, executable)
        if (diagnostics.isEmpty()) {
            return ProblemDescriptor.EMPTY_ARRAY
        }

        return diagnostics.mapNotNull { diagnostic ->
            diagnostic.toProblemDescriptor(file, document, manager, isOnTheFly)
        }.toTypedArray()
    }

    private fun runOxlint(
        project: com.intellij.openapi.project.Project,
        virtualFile: VirtualFile,
        oxlint: OxlintPackage,
        settings: OxlintSettings,
        executable: String,
    ): List<OxlintDiagnostic> {
        val workingDirectory = ReadAction.compute<String?, Throwable> {
            ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(virtualFile)?.path
                ?: project.basePath
                ?: virtualFile.parent?.path
        }

        return runCatching {
            val targetRun = OxkTargetRunBuilder(project)
                .getBuilder(ConfigurationMode.MANUAL, executable)
                .setWorkingDirectory(workingDirectory)
                .addParameters(createOxlintLintParameters(oxlint, settings))
                .addParameters(listOf(ProcessCommandParameter.FilePath(Path(virtualFile.path))))
                .build()

            val output = (targetRun.startProcess() as? CapturingProcessHandler)?.runProcess(OXK_LINT_TIMEOUT_MS)
                ?: return emptyList()
            if (output.isTimeout) {
                LOG.warn("Oxk lint inspection timed out for ${virtualFile.path}")
                return emptyList()
            }

            val diagnostics = parseOxlintJsonDiagnostics(output.stdout.ifBlank { output.stderr })
            if (diagnostics.isEmpty() && output.exitCode != 0) {
                LOG.warn(
                    "Oxk lint inspection returned no diagnostics for ${virtualFile.path}. " +
                        "exitCode=${output.exitCode}, stderr=${output.stderr.take(LOG_SNIPPET_LIMIT)}",
                )
            }
            diagnostics
        }.onFailure {
            LOG.warn("Oxk lint inspection failed for ${virtualFile.path}", it)
        }.getOrDefault(emptyList())
    }

    private fun OxlintDiagnostic.toProblemDescriptor(
        file: PsiFile,
        document: Document,
        manager: InspectionManager,
        isOnTheFly: Boolean,
    ): ProblemDescriptor? {
        val startOffset = toStartOffset(document.lineCount) { line ->
            document.getLineStartOffset(line)
        } ?: return null
        val endOffset = (startOffset + length.coerceAtLeast(1)).coerceAtMost(document.textLength)
        if (startOffset !in 0 until endOffset) {
            return null
        }

        return manager.createProblemDescriptor(
            file,
            TextRange(startOffset, endOffset),
            displayMessage,
            problemHighlightType(),
            isOnTheFly,
        )
    }

    private fun OxlintDiagnostic.problemHighlightType(): ProblemHighlightType =
        when (highlightSeverity) {
            HighlightSeverity.ERROR -> ProblemHighlightType.ERROR
            else -> ProblemHighlightType.WARNING
        }

    companion object {
        private const val OXK_LINT_TIMEOUT_MS = 10_000
        private const val LOG_SNIPPET_LIMIT = 1_000
        private val LOG = Logger.getInstance("#com.github.ohosrs.oxkintellijplugin.oxlint.inspections.OxlintLocalInspection")
    }
}
