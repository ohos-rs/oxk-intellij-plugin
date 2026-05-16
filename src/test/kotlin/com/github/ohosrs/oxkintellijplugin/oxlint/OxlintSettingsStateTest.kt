package com.github.ohosrs.oxkintellijplugin.oxlint

import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OxlintSettingsStateTest {
    @Test
    fun defaultExtensionsKeepUpstreamOxcExtensionsAndArkTs() {
        assertEquals(
            listOf(
                ".astro",
                ".js", ".jsx", ".cjs", ".mjs",
                ".svelte",
                ".ts", ".tsx", ".cts", ".mts",
                ".vue",
            ),
            OxlintSettingsState.OXC_DEFAULT_EXTENSION_LIST,
        )
        assertTrue(OxlintSettingsState.DEFAULT_EXTENSION_LIST.containsAll(OxlintSettingsState.OXC_DEFAULT_EXTENSION_LIST))
        assertTrue(OxlintSettingsState.DEFAULT_EXTENSION_LIST.contains(".ets"))
    }
}
