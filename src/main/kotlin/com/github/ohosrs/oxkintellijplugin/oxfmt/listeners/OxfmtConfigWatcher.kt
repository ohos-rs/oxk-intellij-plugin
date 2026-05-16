package com.github.ohosrs.oxkintellijplugin.oxfmt.listeners

import com.github.ohosrs.oxkintellijplugin.extensions.isOxfmtConfigFile
import com.github.ohosrs.oxkintellijplugin.oxfmt.services.OxfmtServerService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent

class OxfmtConfigWatcher : BulkFileListener {

    override fun after(events: List<VFileEvent>) {
        val configChanged = events.any { event ->
            return@any event.file?.isOxfmtConfigFile() == true
        }

        if (configChanged) {
            val projectManager = ApplicationManager.getApplication().service<ProjectManager>()
            val openProjects = projectManager.openProjects
            openProjects.forEach { project ->
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        OxfmtServerService.getInstance(project).restartServer()
                    }
                }
            }
        }
    }
}
