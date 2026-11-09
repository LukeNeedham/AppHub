package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.decodeText
import com.lukeneedham.apphub.data.relevantPartOfLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CiLogTest {

    @Test
    fun theErrorLinesComeFirstWithoutTimestampsOrColours() {
        val log = listOf(
            "2026-11-09T10:00:00.1234567Z > Task :app:compileDebugKotlin",
            "2026-11-09T10:00:01.0000000Z e: file:///a/Hint.kt:3:5 Unresolved reference: Foo",
            "2026-11-09T10:00:02.0000000Z \u001B[31mFAILED\u001B[0m",
            "2026-11-09T10:00:03.0000000Z BUILD FAILED in 4s",
        ).joinToString("\n")

        val part = relevantPartOfLog(log)

        assertTrue(part.startsWith("Error lines:\ne: file:///a/Hint.kt:3:5 Unresolved reference: Foo\nFAILED"))
        assertTrue(part.contains("End of the log:"))
        assertTrue(!part.contains("2026-11-09"))
    }

    @Test
    fun theEndOfALongLogIsCut() {
        val part = relevantPartOfLog("line\n".repeat(5_000), tailChars = 100)

        assertTrue(part.length < 200)
    }

    @Test
    fun onlyValidUtf8IsText() {
        assertEquals("héllo", decodeText("héllo".toByteArray()))
        assertNull(decodeText(byteArrayOf(0x68, 0xC3.toByte(), 0x28)))
    }
}
