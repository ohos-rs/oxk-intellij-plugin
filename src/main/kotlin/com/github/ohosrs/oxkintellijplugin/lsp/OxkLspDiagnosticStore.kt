package com.github.ohosrs.oxkintellijplugin.lsp

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import org.eclipse.lsp4j.Diagnostic

@Service(Service.Level.PROJECT)
class OxkLspDiagnosticStore(private val project: Project) {
    private val diagnosticsByUri = ConcurrentHashMap<String, List<Diagnostic>>()

    fun diagnostics(uri: String): List<Diagnostic> =
        diagnosticsByUri[uri].orEmpty()

    fun update(uri: String, diagnostics: List<Diagnostic>) {
        if (project.isDisposed) {
            return
        }

        if (diagnostics.isEmpty()) {
            diagnosticsByUri.remove(uri)
        } else {
            diagnosticsByUri[uri] = diagnostics.toList()
        }
    }

    fun clear() {
        diagnosticsByUri.clear()
    }

    companion object {
        fun getInstance(project: Project): OxkLspDiagnosticStore =
            project.getService(OxkLspDiagnosticStore::class.java)
    }
}
