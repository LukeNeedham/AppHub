package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.NewAppStack
import com.lukeneedham.apphub.domain.newAppDispatchInputs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewAppStackTest {

    @Test
    fun `the ids are the ones the workflow takes as its stack input`() {
        // `stack` of `.github/workflows/new_app.yml` in ci-workflows: options [kmp, next-js]
        assertEquals(listOf("kmp", "next-js"), NewAppStack.entries.map { it.id })
    }

    @Test
    fun `the workflow is started with the name, the stack and resume`() {
        assertEquals(
            mapOf("name" to "MySite", "stack" to "next-js", "resume" to "true"),
            newAppDispatchInputs("MySite", NewAppStack.NextJs),
        )
        assertEquals("kmp", newAppDispatchInputs("LyricTutor", NewAppStack.Kmp).getValue("stack"))
    }

    @Test
    fun `only an app has an Android package`() {
        assertTrue(NewAppStack.Kmp.hasAndroidPackage)
        assertFalse(NewAppStack.NextJs.hasAndroidPackage)
    }

    @Test
    fun `the repo is described as an app or as a website`() {
        // As `create_repo` of `scripts/new_app.py` in ci-workflows words it
        assertEquals("LyricTutor, an Android app", NewAppStack.Kmp.repoDescription("LyricTutor"))
        assertEquals("MySite, a website", NewAppStack.NextJs.repoDescription("MySite"))
    }

    @Test
    fun `every stack says what it is`() {
        NewAppStack.entries.forEach {
            assertTrue(it.title.isNotBlank())
            assertTrue(it.description.isNotBlank())
        }
    }
}
