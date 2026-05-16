package com.github.ohosrs.oxkintellijplugin.oxlint

import org.junit.Assert.assertTrue
import org.junit.Test

class OxlintSchemaTest {
    @Test
    fun `oxlint schema supports arkts plugin`() {
        val schema = javaClass.getResource("/jsonSchemas/oxlint-configuration-schema.json")
            ?.readText()
            .orEmpty()

        assertTrue(schema.contains("\"arkts\""))
    }
}
