package com.github.ohosrs.oxkintellijplugin.oxlint

import com.github.ohosrs.oxkintellijplugin.ConfigurationMode
import com.github.ohosrs.oxkintellijplugin.extensions.assertOxkLintReports
import com.github.ohosrs.oxkintellijplugin.extensions.useLocalOxkBinaryIfAvailable
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.builders.ModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.ModuleFixture

@TestDataPath("\$CONTENT_ROOT/testData/oxlint/highlighting")
class CustomConfigHighlightingTest :
    CodeInsightFixtureTestCase<ModuleFixtureBuilder<ModuleFixture>>() {

    override fun setUp() {
        super.setUp()
        myFixture.testDataPath = "src/test/testData/oxlint/highlighting"

        val oxlintSettings = OxlintSettings.getInstance(myFixture.project)
        oxlintSettings.configPath = "${myFixture.tempDirPath}/custom-oxlint.jsonc"
        oxlintSettings.configurationMode = ConfigurationMode.MANUAL
        myFixture.useLocalOxkBinaryIfAvailable()
    }

    fun testRootFileHighlighting() {
        myFixture.copyDirectoryToProject("custom-config", "")

        myFixture.assertOxkLintReports("index.js", "no-debugger", "curly")
    }
}
