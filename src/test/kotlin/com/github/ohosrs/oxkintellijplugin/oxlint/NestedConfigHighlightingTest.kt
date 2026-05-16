package com.github.ohosrs.oxkintellijplugin.oxlint

import com.github.ohosrs.oxkintellijplugin.extensions.assertOxkLintReports
import com.github.ohosrs.oxkintellijplugin.extensions.useLocalOxkBinaryIfAvailable
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class NestedConfigHighlightingTest :
    CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    override fun setUp() {
        super.setUp()
        myFixture.testDataPath = "src/test/testData/oxlint/highlighting"
        myFixture.useLocalOxkBinaryIfAvailable()
    }

    fun testRootFileHighlighting() {
        myFixture.copyDirectoryToProject("nested-config", "")

        myFixture.assertOxkLintReports("index.js", "no-debugger", "no-new-array", "curly")
    }

    fun testSubdirectoryFileHighlighting() {
        myFixture.copyDirectoryToProject("nested-config", "")

        myFixture.assertOxkLintReports("subdirectory/index.js", "no-new-array")
    }
}
