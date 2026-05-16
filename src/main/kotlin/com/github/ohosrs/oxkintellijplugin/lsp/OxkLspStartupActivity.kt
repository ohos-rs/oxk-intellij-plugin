package com.github.ohosrs.oxkintellijplugin.lsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class OxkLspStartupActivity : StartupActivity.DumbAware {
    override fun runActivity(project: Project) {
        OxkLspServerService.getInstance(project).activate()
    }
}
