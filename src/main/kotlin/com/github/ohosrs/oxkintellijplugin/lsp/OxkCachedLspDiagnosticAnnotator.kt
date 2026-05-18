package com.github.ohosrs.oxkintellijplugin.lsp

import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class OxkCachedLspDiagnosticAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        val project = file.project.takeUnless { it.isDisposed } ?: return
        val virtualFile = file.virtualFile ?: return
        val settings = OxlintSettings.getInstance(project)
        if (!settings.isEnabled() || !settings.fileSupported(virtualFile)) {
            return
        }

        val document = file.viewProvider.document ?: return
        val uri = OxkLspServerService.getInstance(project).ensureDocumentOpened(virtualFile, document) ?: return
        val diagnostics = OxkLspDiagnosticStore.getInstance(project).diagnostics(uri)
        holder.applyOxkDiagnostics(document, diagnostics)
    }
}
