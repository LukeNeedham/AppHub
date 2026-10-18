package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.DeepLink
import com.lukeneedham.apphub.domain.PullRequestRef
import com.lukeneedham.apphub.domain.pullRequestDeepLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {

    @Test
    fun readsTheRepoAndNumber() {
        assertEquals(
            PullRequestRef("LukeNeedham/FlagTutor", 12),
            DeepLink.parse("https://www.lukeneedham.com/apphub/pr/FlagTutor/12"),
        )
        assertEquals("LukeNeedham/FlagTutor#12", DeepLink.parse("https://www.lukeneedham.com/apphub/pr/FlagTutor/12")?.key)
    }

    @Test
    fun toleratesATrailingSlashQueryAndFragment() {
        val expected = PullRequestRef("LukeNeedham/App.Hub_2", 3)
        assertEquals(expected, DeepLink.parse("https://www.lukeneedham.com/apphub/pr/App.Hub_2/3/"))
        assertEquals(expected, DeepLink.parse("https://www.lukeneedham.com/apphub/pr/App.Hub_2/3?utm=x#top"))
        assertEquals(expected, DeepLink.parse("https://www.LukeNeedham.com/apphub/pr/App.Hub_2/3"))
    }

    @Test
    fun linksThatAreNotPullRequestsAreIgnored() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse("http://www.lukeneedham.com/apphub/pr/FlagTutor/12"))
        assertNull(DeepLink.parse("https://example.com/PR/FlagTutor/12"))
        assertNull(DeepLink.parse("https://lukeneedham.com/apphub/pr/FlagTutor/12"))
        assertNull(DeepLink.parse("https://evil.lukeneedham.com/apphub/pr/FlagTutor/12"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/pr/FlagTutor/12"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/apphub/pr/12"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/pr/FlagTutor/12"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/apphub/PR/FlagTutor/12"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/other/pr/FlagTutor/12"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/apphub/pr/FlagTutor/twelve"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/apphub/pr/FlagTutor/0"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/apphub/pr/FlagTutor/12/extra"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com/apphub/pr/Flag%20Tutor/12"))
        assertNull(DeepLink.parse("https://www.lukeneedham.com"))
    }

    @Test
    fun aLinkBuiltForAPullRequestReadsBack() {
        val link = pullRequestDeepLink("LukeNeedham/FlagTutor", 12)
        assertEquals("https://www.lukeneedham.com/apphub/pr/FlagTutor/12", link)
        assertEquals(PullRequestRef("LukeNeedham/FlagTutor", 12), DeepLink.parse(link))
    }
}
