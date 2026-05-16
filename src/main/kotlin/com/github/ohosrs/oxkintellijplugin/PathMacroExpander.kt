package com.github.ohosrs.oxkintellijplugin

import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.project.Project

fun expandConfiguredPath(path: String?, project: Project): String {
    val rawPath = path?.takeIf(String::isNotBlank) ?: return ""
    val ideExpandedPath = runCatching {
        PathMacroManager.getInstance(project).expandPath(rawPath)
    }.getOrDefault(rawPath)

    return expandKnownPathMacros(ideExpandedPath, project.basePath)
}

internal fun expandKnownPathMacros(path: String, projectBasePath: String?): String {
    return path
        .replace("\$USER_HOME$", System.getProperty("user.home").orEmpty())
        .replace("\$PROJECT_DIR$", projectBasePath.orEmpty())
}
