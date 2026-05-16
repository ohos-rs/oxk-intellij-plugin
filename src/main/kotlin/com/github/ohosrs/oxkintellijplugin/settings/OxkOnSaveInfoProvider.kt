package com.github.ohosrs.oxkintellijplugin.settings

import com.github.ohosrs.oxkintellijplugin.oxfmt.OxfmtBundle
import com.github.ohosrs.oxkintellijplugin.oxfmt.settings.OxfmtOnSaveFixAllActionInfo
import com.github.ohosrs.oxkintellijplugin.oxlint.OxlintBundle
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintOnSaveFixAllActionInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveContext
import com.intellij.ide.actionsOnSave.ActionOnSaveInfo
import com.intellij.ide.actionsOnSave.ActionOnSaveInfoProvider

class OxkOnSaveInfoProvider : ActionOnSaveInfoProvider() {

    override fun getActionOnSaveInfos(context: ActionOnSaveContext): List<ActionOnSaveInfo> =
        listOf(OxlintOnSaveFixAllActionInfo(context), OxfmtOnSaveFixAllActionInfo(context))

    override fun getSearchableOptions(): Collection<String> {
        return listOf(
            OxlintBundle.message("oxlint.fix.all.on.save.checkbox.on.actions.on.save.page"),
            OxfmtBundle.message("oxfmt.fix.all.on.save.checkbox.on.actions.on.save.page"))
    }
}
