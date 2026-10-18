package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.newClaudeSessionUrl
import com.lukeneedham.apphub.domain.repoUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class RepoLinksTest {

    @Test
    fun repoUrlIsTheGithubPage() {
        assertEquals("https://github.com/LukeNeedham/AppHub", repoUrl("LukeNeedham/AppHub"))
    }

    @Test
    fun newClaudeSessionUrlEncodesTheRepo() {
        assertEquals(
            "https://claude.ai/code/new?repo=LukeNeedham%2FAppHub",
            newClaudeSessionUrl("LukeNeedham/AppHub"),
        )
    }
}
