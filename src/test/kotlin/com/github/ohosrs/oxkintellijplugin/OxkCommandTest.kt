package com.github.ohosrs.oxkintellijplugin

import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Test

class OxkCommandTest {
    @Test
    fun buildOxkCommand_resolvesPnpmShellWrapperToNodeScript() {
        val directory = Files.createTempDirectory("oxk-command-test")
        try {
            val script = directory.resolve(
                Paths.get(
                    "global",
                    "5",
                    ".pnpm",
                    "@ohos-rs+oxk@0.5.0",
                    "node_modules",
                    "@ohos-rs",
                    "oxk",
                    "bin",
                    "oxk.js",
                ),
            )
            Files.createDirectories(script.parent)
            Files.writeString(script, "#!/usr/bin/env node\n")

            val wrapper = directory.resolve("oxk")
            Files.writeString(
                wrapper,
                """
                #!/bin/sh
                basedir=$(dirname "$(echo "$0" | sed -e 's,\\,/,g')")
                exec node "${'$'}basedir/global/5/.pnpm/@ohos-rs+oxk@0.5.0/node_modules/@ohos-rs/oxk/bin/oxk.js" "$@"
                """.trimIndent(),
            )

            val command = buildOxkCommand(
                wrapper.toString(),
                listOf(
                    ProcessCommandParameter.Value("lint"),
                    ProcessCommandParameter.Value("--lsp"),
                ),
            )

            assertEquals(script.toString(), command[1])
            assertEquals(listOf("lint", "--lsp"), command.drop(2))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
