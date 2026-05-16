package com.github.ohosrs.oxkintellijplugin

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginXmlTest {
    @Test
    fun lintDiagnosticsUseOwnLspSessionWithoutRegisteringDevEcoServerDefinitions() {
        val pluginXml = Files.readString(Path.of("src", "main", "resources", "META-INF", "plugin.xml"))

        assertTrue(pluginXml.contains("OxkLspStartupActivity"))
        assertTrue(pluginXml.contains("OxkCachedLspDiagnosticAnnotator"))
        assertTrue(pluginXml.contains("""language="JavaScript""""))
        assertTrue(pluginXml.contains("""language="TypeScript""""))
        assertTrue(pluginXml.contains("""language="ExtendTypeScript""""))
        assertFalse(pluginXml.contains("externalAnnotator"))
        assertFalse(pluginXml.contains("OxlintLocalInspection"))
        assertFalse(pluginXml.contains("platform.lsp.serverSupportProvider"))
        assertFalse(pluginXml.contains("OxkLintServerDefinition"))
        assertFalse(pluginXml.contains("IntellijLanguageClient"))
    }
}
