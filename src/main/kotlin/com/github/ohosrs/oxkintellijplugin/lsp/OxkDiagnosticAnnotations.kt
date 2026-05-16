package com.github.ohosrs.oxkintellijplugin.lsp

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity

internal fun AnnotationHolder.applyOxkDiagnostics(document: Document, diagnostics: List<Diagnostic>) {
    diagnostics.distinctBy { it.deduplicationKey() }.forEach { diagnostic ->
        val range = diagnostic.toTextRange(document) ?: return@forEach
        newAnnotation(diagnostic.toHighlightSeverity(), diagnostic.displayMessage())
            .range(range)
            .create()
    }
}

private fun Diagnostic.displayMessage(): String {
    val codeText = code?.left ?: code?.right?.toString()
    return codeText?.takeIf(String::isNotBlank)?.let { "$message ($it)" } ?: message.orEmpty()
}

private fun Diagnostic.toHighlightSeverity(): HighlightSeverity =
    when (severity) {
        DiagnosticSeverity.Error -> HighlightSeverity.ERROR
        DiagnosticSeverity.Information -> HighlightSeverity.INFORMATION
        DiagnosticSeverity.Hint -> HighlightSeverity.WEAK_WARNING
        DiagnosticSeverity.Warning,
        null -> HighlightSeverity.WARNING
    }

private fun Diagnostic.toTextRange(document: Document): TextRange? {
    val diagnosticRange = range ?: return null
    val startOffset = document.offsetAt(diagnosticRange.start.line, diagnosticRange.start.character) ?: return null
    val rawEndOffset = document.offsetAt(diagnosticRange.end.line, diagnosticRange.end.character) ?: startOffset
    val endOffset = rawEndOffset.coerceAtLeast(startOffset + 1).coerceAtMost(document.textLength)
    if (startOffset !in 0 until endOffset) {
        return null
    }
    return TextRange(startOffset, endOffset)
}

private fun Document.offsetAt(line: Int, character: Int): Int? {
    if (line !in 0 until lineCount) {
        return null
    }
    val lineStart = getLineStartOffset(line)
    val lineEnd = getLineEndOffset(line)
    return (lineStart + character.coerceAtLeast(0)).coerceAtMost(lineEnd)
}

internal fun Diagnostic.deduplicationKey(): String =
    listOf(
        range?.start?.line,
        range?.start?.character,
        range?.end?.line,
        range?.end?.character,
        source,
        code?.left ?: code?.right?.toString(),
        message,
    ).joinToString("|")
