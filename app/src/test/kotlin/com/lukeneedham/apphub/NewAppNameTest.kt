package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.newAppNameError
import com.lukeneedham.apphub.domain.newAppPackage
import com.lukeneedham.apphub.domain.newAppRunTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NewAppNameTest {

    @Test
    fun `a normal name is fine`() {
        for (name in listOf("LyricTutor", "Stickerboard", "LT-Companion", "my_app2", "Ab")) {
            assertNull(name, newAppNameError(name))
        }
    }

    @Test
    fun `a name GitHub or the script would refuse is not fine`() {
        val bad = listOf("", "A", "1App", "-app", "app-", "my app", "my--app", "a/b", "../x", "app.git", "x".repeat(51), "App\n", "é")
        for (name in bad) {
            assertNotNull("'$name'", newAppNameError(name))
        }
    }

    @Test
    fun `the package is the lower case letters and digits of the name`() {
        assertEquals("com.lukeneedham.lyrictutor", newAppPackage("LyricTutor"))
        assertEquals("com.lukeneedham.ltcompanion", newAppPackage("LT-Companion"))
        assertEquals("com.lukeneedham.myapp2", newAppPackage("my_app2"))
    }

    @Test
    fun `the run is titled as the workflow names it`() {
        // The run-name of new_app.yml in ci-workflows: `New App ${{ inputs.name }}`
        assertEquals("New App LyricTutor", newAppRunTitle("LyricTutor"))
    }
}
