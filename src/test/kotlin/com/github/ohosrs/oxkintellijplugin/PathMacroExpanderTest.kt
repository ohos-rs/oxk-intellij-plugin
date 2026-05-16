package com.github.ohosrs.oxkintellijplugin

import org.junit.Assert.assertEquals
import org.junit.Test

class PathMacroExpanderTest {
    @Test
    fun expandKnownPathMacros_expandsUserHomeAndProjectDir() {
        val home = System.getProperty("user.home")

        assertEquals(
            "$home/bin/oxk:/tmp/project/.oxlintrc.json",
            expandKnownPathMacros(
                "\$USER_HOME$/bin/oxk:\$PROJECT_DIR$/.oxlintrc.json",
                "/tmp/project",
            ),
        )
    }
}
