package com.github.ohosrs.oxkintellijplugin.oxfmt.settings

import com.github.ohosrs.oxkintellijplugin.ConfigurationMode
import com.github.ohosrs.oxkintellijplugin.expandConfiguredPath
import com.github.ohosrs.oxkintellijplugin.oxfmt.settings.OxfmtSettingsState.Companion.DEFAULT_EXTENSION_LIST
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

@Service(Service.Level.PROJECT)
@State(name = "OxfmtSettings", storages = [Storage("OxfmtSettings.xml")],
    category = SettingsCategory.TOOLS)
class OxfmtSettings(private val project: Project) :
    SimplePersistentStateComponent<OxfmtSettingsState>(OxfmtSettingsState()) {

    var configurationMode: ConfigurationMode
        get() = state.configurationMode
        set(value) {
            state.configurationMode = value
        }

    var supportedExtensions: MutableList<String>
        get() = state.supportedExtensions.takeIf { it.isNotEmpty() }
                ?: DEFAULT_EXTENSION_LIST.toMutableList()
        set(value) {
            state.supportedExtensions.clear()
            state.supportedExtensions.addAll(value)
        }

    var binaryPath: String
        get() = expandPath(state.binaryPath)
        set(value) {
            state.binaryPath = value
        }

    var configPath: String
        get() = expandPath(state.configPath)
        set(value) {
            val file = File(value)
            if (file.isFile) {
                state.configPath = file.path
                return
            }
            state.configPath = value
        }

    var fixAllOnSave: Boolean
        get() = isEnabled() && state.fixAllOnSave
        set(value) {
            state.fixAllOnSave = value
        }

    fun isEnabled(): Boolean {
        return configurationMode !== ConfigurationMode.DISABLED
    }

    fun fileSupported(file: VirtualFile): Boolean {
        val fileExtension = file.extension
        return if (fileExtension != null) {
            supportedExtensions.contains(".$fileExtension")
        } else {
            false
        }
    }

    private fun expandPath(path: String?): String {
        return expandConfiguredPath(path, project)
    }

    companion object {

        @JvmStatic
        fun getInstance(project: Project): OxfmtSettings = project.service()
    }
}
