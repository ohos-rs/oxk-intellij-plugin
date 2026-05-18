package com.github.ohosrs.oxkintellijplugin.lsp

import com.intellij.testFramework.LightVirtualFile
import org.eclipse.lsp4j.InitializeParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OxkLintServerDefinitionTest {
    @Test
    fun configureOxkClientCapabilities_requestsVersionedDiagnostics() {
        val params = InitializeParams()

        configureOxkClientCapabilities(params)

        assertEquals(true, params.capabilities.workspace.configuration)
        assertTrue(params.capabilities.textDocument.publishDiagnostics.versionSupport)
    }

    @Test
    fun normalizeLspExtension_removesDotPrefix() {
        assertEquals("ets", normalizeLspExtension(".ets"))
        assertEquals("ts", normalizeLspExtension("ts"))
    }

    @Test
    fun languageIdForOxkExtension_keepsOxcExtensionsAndArkTs() {
        assertEquals("astro", languageIdForOxkExtension(".astro"))
        assertEquals("svelte", languageIdForOxkExtension(".svelte"))
        assertEquals("vue", languageIdForOxkExtension(".vue"))
        assertEquals("typescript", languageIdForOxkExtension(".ets"))
        assertEquals("typescriptreact", languageIdForOxkExtension(".tsx"))
    }

    @Test
    fun oxkUriOrNull_ignoresLightVirtualFiles() {
        assertNull(LightVirtualFile("Dummy.ts", "const value = 1").oxkUriOrNull())
    }
}
