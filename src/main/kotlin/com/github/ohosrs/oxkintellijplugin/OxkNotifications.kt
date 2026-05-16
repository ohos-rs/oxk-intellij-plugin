package com.github.ohosrs.oxkintellijplugin

import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintConfigurable
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project

fun notifyOxkExecutableNotFound(project: Project) {
    NotificationGroupManager.getInstance()
        .getNotificationGroup(NOTIFICATION_GROUP)
        .createNotification(
            "Oxk executable not found",
            "Set OXK_BINARY_PATH, install @ohos-rs/oxk in the project, or configure a binary with lint LSP support in Oxk settings.",
            NotificationType.WARNING,
        )
        .addAction(NotificationAction.createSimple("Open Oxk Settings") {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, OxlintConfigurable::class.java)
        })
        .notify(project)
}
