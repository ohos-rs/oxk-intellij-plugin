package com.github.ohosrs.oxkintellijplugin.oxlint

import com.github.ohosrs.oxkintellijplugin.extensions.isOxlintJsonConfigFile
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import org.jetbrains.annotations.Nls

class OxlintSchemaProviderFactory : JsonSchemaProviderFactory {

    override fun getProviders(project: Project): List<JsonSchemaFileProvider?> {
        return listOf(object : JsonSchemaFileProvider {
            override fun isAvailable(file: VirtualFile): Boolean {
                val settings = OxlintSettings.getInstance(project)
                return settings.state.configPath == file.path || file.isOxlintJsonConfigFile()
            }

            override fun getName(): @Nls String {
                return OxlintBundle.message("oxlint.schema.name")
            }

            override fun getSchemaFile(): VirtualFile? {
                return JsonSchemaProviderFactory.getResourceFile(
                    javaClass,
                    "/jsonSchemas/oxlint-configuration-schema.json"
                )
            }

            override fun getSchemaType(): SchemaType {
                return SchemaType.schema
            }
        })
    }
}
