package com.github.ohosrs.oxkintellijplugin

import com.github.ohosrs.oxkintellijplugin.extensions.isOxfmtConfigFile
import com.github.ohosrs.oxkintellijplugin.extensions.isOxlintConfigFile
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import javax.swing.Icon

class OxkIconProvider : IconProvider(), DumbAware {

    override fun getIcon(element: PsiElement,
        flags: Int): Icon? {
        if (element !is PsiFile) {
            return null
        }
        val file = element.viewProvider.virtualFile
        if (!file.isValid || file.isDirectory) {
            return null
        }
        val settings = OxlintSettings.getInstance(element.project)
        if (settings.state.configPath == file.path) {
            return OxkIcons.OxcRound
        }
        if (file.isOxlintConfigFile()) {
            return OxkIcons.OxcRound
        }
        if (file.isOxfmtConfigFile()) {
            return OxkIcons.OxcRound
        }

        return null
    }
}
