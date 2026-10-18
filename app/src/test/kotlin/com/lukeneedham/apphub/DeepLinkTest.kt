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
            DeepLink.parse("https://lukeneedham.app/PR/FlagTutor/12"),
        )
        assertEquals("LukeNeedham/FlagTutor#12", DeepLink.parse("https://lukeneedham.app/PR/FlagTutor/12")?.key)
    }

    @Test
    fun toleratesATrailingSlashQueryAndFragment() {
        val expected = PullRequestRef("LukeNeedham/App.Hub_2", 3)
        assertEquals(expected, DeepLink.parse("https://lukeneedham.app/PR/App.Hub_2/3/"))
        assertEquals(expected, DeepLink.parse("https://lukeneedham.app/PR/App.Hub_2/3?utm=x#top"))
        assertEquals(expected, DeepLink.parse("https://LukeNeedham.app/PR/App.Hub_2/3"))
    }

    @Test
    fun linksThatAreNotPullRequestsAreIgnored() {
        assertNull(DeepLink.parse(null))
        assertNull(DeepLink.parse("http://lukeneedham.app/PR/FlagTutor/12"))
        assertNull(DeepLink.parse("https://example.com/PR/FlagTutor/12"))
        assertNull(DeepLink.parse("https://evil.lukeneedham.app/PR/FlagTutor/12"))
        assertNull(DeepLink.parse("https://lukeneedham.app/pr/FlagTutor/12"))
        assertNull(DeepLink.parse("https://lukeneedham.app/PR/12"))
        assertNull(DeepLink.parse("https://lukeneedham.app/PR/FlagTutor/twelve"))
        assertNull(DeepLink.parse("https://lukeneedham.app/PR/FlagTutor/0"))
        assertNull(DeepLink.parse("https://lukeneedham.app/PR/FlagTutor/12/extra"))
        assertNull(DeepLink.parse("https://lukeneedham.app/PR/Flag%20Tutor/12"))
        assertNull(DeepLink.parse("https://lukeneedham.app"))
    }

    @Test
    fun aLinkBuiltForAPullRequestReadsBack() {
        val link = pullRequestDeepLink("LukeNeedham/FlagTutor", 12)
        assertEquals("https://lukeneedham.app/PR/FlagTutor/12", link)
        assertEquals(PullRequestRef("LukeNeedham/FlagTutor", 12), DeepLink.parse(link))
    }
}
