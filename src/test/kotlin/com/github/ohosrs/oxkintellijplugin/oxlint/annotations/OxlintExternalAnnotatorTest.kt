package com.github.ohosrs.oxkintellijplugin.oxlint.annotations

import org.junit.Assert.assertEquals
import org.junit.Test

class OxlintExternalAnnotatorTest {
    @Test
    fun parseOxlintJsonDiagnostics_readsPrimaryLabel() {
        val diagnostics = parseOxlintJsonDiagnostics(
            """
            {
              "diagnostics": [{
                "message": "`debugger` statement is not allowed",
                "code": "eslint(no-debugger)",
                "severity": "warning",
                "labels": [{
                  "span": {"offset": 37, "length": 8, "line": 3, "column": 1}
                }]
              }]
            }
            """.trimIndent(),
        )

        assertEquals(1, diagnostics.size)
        assertEquals("`debugger` statement is not allowed", diagnostics.single().message)
        assertEquals("eslint(no-debugger)", diagnostics.single().code)
        assertEquals(37, diagnostics.single().offset)
        assertEquals(3, diagnostics.single().line)
        assertEquals(1, diagnostics.single().column)
        assertEquals(8, diagnostics.single().length)
    }
}
