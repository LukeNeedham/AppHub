package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.ApkBuild
import com.lukeneedham.apphub.domain.CommentParser
import com.lukeneedham.apphub.domain.PreviewChange
import com.lukeneedham.apphub.domain.PreviewChangeKind
import com.lukeneedham.apphub.domain.WebsitePreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentParserTest {

    private val apk = "https://github.com/LukeNeedham/FlagTutor/releases/download/my-branch-12-1/app-debug.apk"

    private val succeeded = """
        ### ✅ Build succeeded

        - **APK:** 📦 [Download]($apk)
        - **Commit:** [`be7d718`](https://github.com/LukeNeedham/FlagTutor/commit/be7d718aaa)
        - **Built:** 09/10/2026 12:56:20
        - **Run:** [#210](https://github.com/LukeNeedham/FlagTutor/actions/runs/1)
    """.trimIndent()

    @Test
    fun succeededComment() {
        assertEquals(ApkBuild.Succeeded(apk, "be7d718", "09/10/2026 12:56:20"), CommentParser.parseApkBuild(succeeded))
    }

    @Test
    fun theAppHubLinkCommentIsNeitherABuildNorAWebDeploy() {
        val body = """
            ### 📲 AppHub

            [Open this pull request in AppHub](https://www.lukeneedham.com/apphub/pr/FlagTutor/12)
        """.trimIndent()
        assertNull(CommentParser.parseApkBuild(body))
        assertNull(CommentParser.parseWebUrl(body))
        assertEquals(ApkBuild.Succeeded(apk, "be7d718", "09/10/2026 12:56:20") to null, CommentParser.parse(listOf(body, succeeded)))
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
    fun inProgressKeepsTheRunLink() {
        val run = "https://github.com/x/y/actions/runs/40145571300"
        val body = """
            ### ⏳ Build in progress

            - **Commit:** [`1234567`](https://github.com/x/y/commit/1234567)
            - **Started:** 25/10/2026 02:09:25
            - **Run:** [#108]($run)
        """.trimIndent()
        assertEquals(ApkBuild.InProgress("1234567", null, run), CommentParser.parseApkBuild(body))
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

    private val previewsComment = """
        <!-- previews-diff -->
        ### 🖼️ Preview changes

        Compared with `main` at `44b208e`; this PR at `7ac370c`. 10 previews rendered.

        **3 changed:** 1 new, 1 modified, 1 removed.

        | Preview | Before (main) | After (this PR) |
        |---|---|---|
        | `SettingsPage · SettingsPageScreenshot` (modified) | <img src="https://example.com/main/Settings.png" width="180" alt="x"> | <img src="https://example.com/pr/Settings.png" width="180" alt="x"> |
        | `Pill · PillNew` (new) | – | <img src="https://example.com/pr/Pill.png" width="180" alt="x"> |
        | `Old · OldPreview` (removed) | ![x](https://example.com/main/Old.png) | – |

        All of this PR's previews are assets of its [build release](https://github.com/x/y/releases/tag/t).
    """.trimIndent()

    @Test
    fun previewChangesAreReadFromTheTable() {
        assertEquals(
            listOf(
                PreviewChange("SettingsPage · SettingsPageScreenshot", PreviewChangeKind.Modified, "https://example.com/main/Settings.png", "https://example.com/pr/Settings.png"),
                PreviewChange("Pill · PillNew", PreviewChangeKind.New, null, "https://example.com/pr/Pill.png"),
                PreviewChange("Old · OldPreview", PreviewChangeKind.Removed, "https://example.com/main/Old.png", null),
            ),
            CommentParser.parsePreviewChanges(previewsComment),
        )
    }

    @Test
    fun previewChangesNameSplitsIntoTitleAndSubtitle() {
        val change = PreviewChange("SettingsPage · SettingsPageScreenshot", PreviewChangeKind.Modified, null, null)
        assertEquals("SettingsPage", change.title)
        assertEquals("SettingsPageScreenshot", change.subtitle)
        assertNull(PreviewChange("Lone", PreviewChangeKind.New, null, null).subtitle)
    }

    @Test
    fun noPreviewChangedMeansAnEmptyList() {
        val body = "<!-- previews-diff -->\n## 🖼️ Preview changes\n\nNo preview changed ✅\n"
        assertEquals(emptyList<PreviewChange>(), CommentParser.parsePreviewChanges(body))
    }

    @Test
    fun otherCommentsAreNotThePreviewChanges() {
        assertNull(CommentParser.parsePreviewChanges(succeeded))
        assertEquals(emptyList<PreviewChange>(), CommentParser.parsePreviewChanges(listOf(succeeded)))
    }

    @Test
    fun theNewestPreviewCommentWins() {
        val none = "<!-- previews-diff -->\nNo preview changed ✅"
        assertEquals(emptyList<PreviewChange>(), CommentParser.parsePreviewChanges(listOf(previewsComment, none)))
        assertEquals(3, CommentParser.parsePreviewChanges(listOf(none, previewsComment)).size)
    }

    @Test
    fun thePreviewCommentIsNotAWebDeploy() {
        assertNull(CommentParser.parseWebUrl(previewsComment))
    }

    @Test
    fun previewChangesAreReadFromTheSectionLayout() {
        val body = """
            <!-- previews-diff -->
            ## 🖼️ Preview changes

            **2 changed:** 1 new, 1 modified, 0 removed.

            ### Changed

            #### PullRequestPage

            **PullRequestPageScreenshot (main → this PR)**

            <img src="https://example.com/main/P.png" width="150" alt="PullRequestPage · PullRequestPageScreenshot"> <img src="https://example.com/pr/P.png" width="150" alt="PullRequestPage · PullRequestPageScreenshot">

            ### New

            #### Pill

            **PillScreenshot (this PR)**

            <img src="https://example.com/pr/Pill.png" width="150" alt="Pill · PillScreenshot">

            All of this PR's previews are assets of its [build release](https://github.com/x/y/releases/tag/t).
        """.trimIndent()
        assertEquals(
            listOf(
                PreviewChange("PullRequestPage · PullRequestPageScreenshot", PreviewChangeKind.Modified, "https://example.com/main/P.png", "https://example.com/pr/P.png"),
                PreviewChange("Pill · PillScreenshot", PreviewChangeKind.New, null, "https://example.com/pr/Pill.png"),
            ),
            CommentParser.parsePreviewChanges(body),
        )
    }

    private val previewUrl = "https://personal-website-git-my-branch-lukeneedhams-projects.vercel.app"
    private val websiteMarker = "<!-- Sticky Pull Request Commentweb-preview-link -->"

    // The comments of `web_pr.yml` in ci-workflows (`scripts/web_preview.py`), with the marker the sticky comment action adds
    private val websiteReady = """
        ### 🌐 Preview

        - **Commit:** `0123456`
        - **Preview:** $previewUrl

        <!-- preview-url: $previewUrl -->
        $websiteMarker
    """.trimIndent()

    private val websiteNeedsLogin = """
        ### 🔒 Preview needs a login

        - **Commit:** `0123456`
        - **Preview:** $previewUrl

        Vercel asks for a login to open this preview. Turn off *Deployment Protection* for previews in the project's settings on Vercel (`docs/next-js-app.md`).

        <!-- preview-url: $previewUrl -->
        $websiteMarker
    """.trimIndent()

    private val websiteNotReady = """
        ### ⏳ Preview not ready

        - **Commit:** `0123456`
        - **Vercel:** in_progress
        $websiteMarker
    """.trimIndent()

    @Test
    fun aWebsitePreviewThatOpensIsReadyWithItsUrl() {
        assertEquals(WebsitePreview.Ready(previewUrl), CommentParser.parseWebsitePreview(websiteReady))
    }

    @Test
    fun aWebsitePreviewThatAsksForALoginSaysSoAndKeepsItsUrl() {
        assertEquals(WebsitePreview.NeedsLogin(previewUrl), CommentParser.parseWebsitePreview(websiteNeedsLogin))
    }

    @Test
    fun aWebsitePreviewThatIsNotThereYetSaysWhatVercelSays() {
        assertEquals(WebsitePreview.NotReady("in_progress"), CommentParser.parseWebsitePreview(websiteNotReady))
        assertNull(WebsitePreview.NotReady("in_progress").url)
    }

    @Test
    fun onlyTheCommentOfTheWebsitePreviewIsOne() {
        assertNull(CommentParser.parseWebsitePreview(succeeded))
        assertNull(CommentParser.parseWebsitePreview("### 📲 AppHub\n\n[Open this pull request in AppHub](https://www.lukeneedham.com/apphub/pr/PersonalWebsite/12)"))
        // The URL on its own, without the comment of the CI around it, is not enough: Vercel's own comment has one too
        assertNull(CommentParser.parseWebsitePreview("<!-- preview-url: $previewUrl -->"))
    }

    @Test
    fun theNewestWebsitePreviewWins() {
        assertEquals(WebsitePreview.Ready(previewUrl), CommentParser.parseWebsitePreview(listOf(websiteNotReady, websiteReady)))
        assertEquals(WebsitePreview.NotReady("in_progress"), CommentParser.parseWebsitePreview(listOf(websiteReady, websiteNotReady)))
        assertNull(CommentParser.parseWebsitePreview(emptyList()))
    }

    @Test
    fun aWebsitePreviewIsNotAnApkBuildAndItsUrlIsAWebLink() {
        assertNull(CommentParser.parseApkBuild(websiteReady))
        assertEquals(null to previewUrl, CommentParser.parse(listOf(websiteReady)))
    }
}
