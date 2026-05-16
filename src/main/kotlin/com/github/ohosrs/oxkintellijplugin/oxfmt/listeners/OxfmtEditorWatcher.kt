package com.github.ohosrs.oxkintellijplugin.oxfmt.listeners

import com.github.ohosrs.oxkintellijplugin.oxfmt.services.OxfmtServerService
import com.github.ohosrs.oxkintellijplugin.oxfmt.settings.OxfmtSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile

class OxfmtEditorWatcher : FileEditorManagerListener {

    override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        val project = source.project
        if (source.allEditors.isEmpty()) {
            OxfmtServerService.getInstance(project).stopServer()
            return
        }

        val stillHasSupportedFileOpen = source.allEditors.any {
            OxfmtSettings.getInstance(project).fileSupported(it.file)
        }
        if (!stillHasSupportedFileOpen) {
            OxfmtServerService.getInstance(project).stopServer()
        }
    }

}
