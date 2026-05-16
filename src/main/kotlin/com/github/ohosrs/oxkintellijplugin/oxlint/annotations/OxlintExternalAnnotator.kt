package com.github.ohosrs.oxkintellijplugin.oxlint.annotations

import com.github.ohosrs.oxkintellijplugin.ConfigurationMode
import com.github.ohosrs.oxkintellijplugin.OxkTargetRunBuilder
import com.github.ohosrs.oxkintellijplugin.ProcessCommandParameter
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintPackage
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintUnusedDisableDirectivesSeverity
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import kotlin.io.path.Path

class OxlintExternalAnnotator : ExternalAnnotator<OxlintAnnotationInput, OxlintAnnotationResult>() {
    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): OxlintAnnotationInput? {
        val project = file.project.takeUnless { it.isDisposed } ?: return null
        val virtualFile = file.virtualFile ?: return null
        val settings = OxlintSettings.getInstance(project)
        if (!settings.isEnabled() || !settings.fileSupported(virtualFile)) {
            return null
        }

        val oxlint = OxlintPackage(project)
        val executable = oxlint.binaryPath(virtualFile) ?: return null
        val workingDirectory = ReadAction.compute<String?, Throwable> {
            ProjectRootManager.getInstance(project).fileIndex.getContentRootForFile(virtualFile)?.path
                ?: project.basePath
                ?: virtualFile.parent?.path
        }

        return OxlintAnnotationInput(
            project = project,
            file = virtualFile,
            executable = executable,
            workingDirectory = workingDirectory,
            parameters = createOxlintLintParameters(oxlint, settings),
        )
    }

    override fun doAnnotate(collectedInfo: OxlintAnnotationInput): OxlintAnnotationResult? {
        if (collectedInfo.project.isDisposed || !collectedInfo.file.isValid) {
            return null
        }

        return runCatching {
            val targetRun = OxkTargetRunBuilder(collectedInfo.project)
                .getBuilder(ConfigurationMode.MANUAL, collectedInfo.executable)
                .setWorkingDirectory(collectedInfo.workingDirectory)
                .addParameters(collectedInfo.parameters)
                .addParameters(listOf(ProcessCommandParameter.FilePath(Path(collectedInfo.file.path))))
                .build()

            val output = (targetRun.startProcess() as? CapturingProcessHandler)?.runProcess(10_000)
                ?: return OxlintAnnotationResult(emptyList())
            if (output.isTimeout) {
                return OxlintAnnotationResult(emptyList())
            }

            OxlintAnnotationResult(parseOxlintJsonDiagnostics(output.stdout))
        }.getOrNull()
    }

    override fun apply(file: PsiFile, annotationResult: OxlintAnnotationResult, holder: AnnotationHolder) {
        val document = file.viewProvider.document ?: return
        annotationResult.diagnostics.forEach { diagnostic ->
            val startOffset = diagnostic.toStartOffset(document.lineCount) { line ->
                document.getLineStartOffset(line)
            } ?: return@forEach
            val endOffset = (startOffset + diagnostic.length.coerceAtLeast(1)).coerceAtMost(document.textLength)
            if (startOffset >= endOffset) {
                return@forEach
            }

            holder.newAnnotation(diagnostic.highlightSeverity, diagnostic.displayMessage)
                .range(TextRange(startOffset, endOffset))
                .create()
        }
    }

}

internal fun createOxlintLintParameters(
    oxlint: OxlintPackage,
    settings: OxlintSettings,
): List<ProcessCommandParameter> {
    val parameters = mutableListOf(
        ProcessCommandParameter.Value("lint"),
        ProcessCommandParameter.Value("--format=json"),
        ProcessCommandParameter.Value("--no-error-on-unmatched-pattern"),
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

data class OxlintAnnotationInput(
    val project: Project,
    val file: VirtualFile,
    val executable: String,
    val workingDirectory: String?,
    val parameters: List<ProcessCommandParameter>,
)

data class OxlintAnnotationResult(
    val diagnostics: List<OxlintDiagnostic>,
)

data class OxlintDiagnostic(
    val message: String,
    val code: String?,
    val severity: String,
    val offset: Int?,
    val line: Int?,
    val column: Int?,
    val length: Int,
) {
    val displayMessage: String
        get() = code?.takeIf(String::isNotBlank)?.let { "$message ($it)" } ?: message

    val highlightSeverity: HighlightSeverity
        get() = when (severity.lowercase()) {
            "error", "deny" -> HighlightSeverity.ERROR
            else -> HighlightSeverity.WARNING
        }

    fun toStartOffset(lineCount: Int, lineStartOffset: (Int) -> Int): Int? {
        val zeroBasedLine = line?.minus(1)
        val zeroBasedColumn = column?.minus(1)
        if (zeroBasedLine != null && zeroBasedColumn != null && zeroBasedLine in 0 until lineCount) {
            return lineStartOffset(zeroBasedLine) + zeroBasedColumn.coerceAtLeast(0)
        }
        return offset
    }
}

internal fun parseOxlintJsonDiagnostics(output: String): List<OxlintDiagnostic> {
    if (output.isBlank()) {
        return emptyList()
    }

    val root = runCatching { JsonParser.parseString(output).asJsonObject }.getOrNull() ?: return emptyList()
    val diagnostics = root.getAsJsonArray("diagnostics") ?: return emptyList()
    return diagnostics.mapNotNull { element ->
        val diagnostic = element.asJsonObject
        val label = diagnostic.getAsJsonArray("labels")?.firstOrNull()?.asJsonObject
        val span = label?.getAsJsonObject("span")

        OxlintDiagnostic(
            message = diagnostic.stringOrNull("message") ?: return@mapNotNull null,
            code = diagnostic.stringOrNull("code"),
            severity = diagnostic.stringOrNull("severity") ?: "warning",
            offset = span?.intOrNull("offset"),
            line = span?.intOrNull("line"),
            column = span?.intOrNull("column"),
            length = span?.intOrNull("length") ?: 1,
        )
    }
}

private fun JsonObject.stringOrNull(name: String): String? =
    get(name)?.takeUnless { it.isJsonNull }?.asString

private fun JsonObject.intOrNull(name: String): Int? =
    get(name)?.takeUnless { it.isJsonNull }?.asInt
