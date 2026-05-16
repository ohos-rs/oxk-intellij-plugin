package com.github.ohosrs.oxkintellijplugin.extensions

import com.github.ohosrs.oxkintellijplugin.ConfigurationMode
import com.github.ohosrs.oxkintellijplugin.oxfmt.settings.OxfmtSettings
import com.github.ohosrs.oxkintellijplugin.oxlint.inspections.OxlintLocalInspection
import com.github.ohosrs.oxkintellijplugin.oxlint.settings.OxlintSettings
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

fun CodeInsightTestFixture.useLocalOxkBinaryIfAvailable() {
    val binary = localOxkBinaryPathForTests()
    val oxfmtSettings = OxfmtSettings.getInstance(project)

    if (binary == null) {
        oxfmtSettings.configurationMode = ConfigurationMode.DISABLED
        return
    }

    val settings = OxlintSettings.getInstance(project)
    settings.binaryPath = binary.toString()
    settings.binaryParameters = mutableListOf("lint", "--lsp")
    settings.configurationMode = ConfigurationMode.MANUAL

    oxfmtSettings.binaryPath = binary.toString()
    oxfmtSettings.configurationMode = ConfigurationMode.MANUAL
}

fun CodeInsightTestFixture.assertOxkLintReports(
    filePath: String,
    vararg expectedRules: String,
) {
    val binary = localOxkBinaryPathForTests()
    assumeTrue("Local oxk binary is not available", binary != null)

    enableInspections(OxlintLocalInspection())
    configureFromTempProjectFile(filePath)
    val text = doHighlighting().joinToString("\n") {
        listOfNotNull(it.description, it.inspectionToolId).joinToString(" ")
    }
    expectedRules.forEach {
        assertTrue("Expected IDE highlighting to contain '$it'. Output:\n$text", text.contains(it))
    }
}

fun localOxkBinaryPathForTests(): Path? {
    val configured = System.getenv("OXK_TEST_BINARY")?.takeIf { it.isNotBlank() }?.let(Paths::get)
    if (configured != null && Files.isExecutable(configured)) {
        return configured.toAbsolutePath().normalize()
    }

    val siblingBuild = Paths.get("..", "oxc-ark", "target", "debug", "oxk")
    if (Files.isExecutable(siblingBuild)) {
        return siblingBuild.toAbsolutePath().normalize()
    }

    return null
}
