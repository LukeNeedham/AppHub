package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.CommentParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentParserTest {

    private val apk = "https://github.com/LukeNeedham/FlagTutor/releases/download/my-branch-12-1/app-debug.apk"

    private val succeeded = """
        ### ✅ Build succeeded

        - **APK:** 📦 [Download]($apk)
        - **AppHub:** 📲 [Open](https://lukeneedham.com/apphub/pr/FlagTutor/12)
        - **Commit:** [`be7d718`](https://github.com/LukeNeedham/FlagTutor/commit/be7d718aaa)
        - **Built:** 09/10/2026 12:56:20
        - **Run:** [#210](https://github.com/LukeNeedham/FlagTutor/actions/runs/1)
    """.trimIndent()

    @Test
    fun succeededComment() {
        assertEquals(ApkBuild.Succeeded(apk, "be7d718"), CommentParser.parseApkBuild(succeeded))
    }

    @Test
    fun theAppHubLinkIsNotTakenAsAWebDeploy() {
        assertNull(CommentParser.parseWebUrl(succeeded))
    }

    @Test
    fun inProgressKeepsTheStaleLink() {
        val body = """
            ### ⏳ Build in progress

            - **Commit:** [`1234567`](https://github.com/x/y/commit/1234567)
            - **Previous APK:** [download]($apk) ⚠️ stale, does not include this commit
        """.trimIndent()
        assertEquals(ApkBuild.InProgress("1234567", apk), CommentParser.parseApkBuild(body))
    }

    @Test
    fun failedAndCancelled() {
        val commit = "- **Commit:** [`abcdef0`](https://github.com/x/y/commit/abcdef0)"
        assertEquals(ApkBuild.Failed("abcdef0"), CommentParser.parseApkBuild("### ❌ Build failed\n\n$commit"))
        assertEquals(ApkBuild.Cancelled("abcdef0"), CommentParser.parseApkBuild("### 🚫 Build cancelled\n\n$commit"))
    }

    @Test
    fun otherCommentsAreNotApkComments() {
        assertNull(CommentParser.parseApkBuild("Looks good, the build succeeded for me"))
        assertNull(CommentParser.parseApkBuild("### ✅ Build succeeded"))
    }

    @Test
    fun webUrlFromADeployComment() {
        val body = "Deploy Preview ready! 🔍 https://deploy-preview-7--flagtutor.netlify.app."
        assertEquals("https://deploy-preview-7--flagtutor.netlify.app", CommentParser.parseWebUrl(body))
    }

    @Test
    fun ordinaryLinksAreNotWebDeploys() {
        assertNull(CommentParser.parseWebUrl("See https://github.com/LukeNeedham/FlagTutor/issues/3 and https://example.com"))
        assertNull(CommentParser.parseWebUrl("https://notnetlify.app.evil.com/x"))
    }

    @Test
    fun newestApkCommentAndWebLinkWin() {
        val (build, web) = CommentParser.parse(
            listOf(
                "preview: https://old.pages.dev",
                succeeded,
                "preview: https://new.pages.dev",
            ),
        )
        assertEquals(ApkBuild.Succeeded(apk, "be7d718"), build)
        assertEquals("https://new.pages.dev", web)
    }

    @Test
    fun sessionLinkFromTheDescriptionOrComments() {
        val session = "https://claude.ai/code/session_01F93afMNjrG448fJEVXhHby"
        assertEquals(session, CommentParser.parseSessionUrl(listOf(null, "Generated with Claude\n\n$session\n")))
        assertEquals("https://claude.ai/code/session_abc", CommentParser.parseSessionUrl(listOf("none", "see https://claude.ai/code/session_abc.")))
        assertNull(CommentParser.parseSessionUrl(listOf("https://claude.ai/chat/123", null)))
    }
}
