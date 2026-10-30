package com.lukeneedham.apphub

import com.lukeneedham.apphub.data.IssueDto
import com.lukeneedham.apphub.data.ReleaseDto
import com.lukeneedham.apphub.data.SearchDto
import com.lukeneedham.apphub.data.WorkflowRunDto
import com.lukeneedham.apphub.data.apiJson
import com.lukeneedham.apphub.data.toMainApk
import com.lukeneedham.apphub.data.toOpenIssue
import com.lukeneedham.apphub.data.toWorkflowRun
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull

class GithubMappersTest {

    @Test
    fun `an empty object decodes to a DTO with nothing in it`() {
        val issue = apiJson.decodeFromString<IssueDto>("{}")
        assertNull(issue.number)
        assertNull(issue.title)
        assertNull(apiJson.decodeFromString<SearchDto>("{}").items)
    }

    @Test
    fun `wrong types and unknown fields do not stop the decoding`() {
        val issue = apiJson.decodeFromString<IssueDto>("""{"number":7,"title":null,"draft":null,"extra":{"a":1}}""")
        assertEquals(7, issue.number)
        assertNull(issue.title)
    }

    @Test
    fun `a search result without a number, a repo or a link is dropped`() {
        assertNull(IssueDto(title = "x", htmlUrl = "https://x", repositoryUrl = "https://api.github.com/repos/o/r").toOpenIssue())
        assertNull(IssueDto(number = 1, htmlUrl = "https://x").toOpenIssue())
        assertNull(IssueDto(number = 1, repositoryUrl = "https://api.github.com/repos/o/r").toOpenIssue())
    }

    @Test
    fun `a search result with only what it needs gets defaults for the rest`() {
        val issue = IssueDto(number = 1, htmlUrl = "https://x", repositoryUrl = "https://api.github.com/repos/o/r").toOpenIssue()!!
        assertEquals("o/r", issue.repo)
        assertEquals("", issue.title)
        assertEquals("", issue.author)
        assertEquals(false, issue.isDraft)
    }

    @Test
    fun `a release without assets is no main build`() {
        assertNull(ReleaseDto(body = "commit: ${"a".repeat(40)}").toMainApk())
    }

    @Test
    fun `a run without an id, a status or a link cannot be followed`() {
        assertNull(WorkflowRunDto(status = "completed", htmlUrl = "https://x").toWorkflowRun())
        assertNull(WorkflowRunDto(id = 1, htmlUrl = "https://x").toWorkflowRun())
        assertNull(WorkflowRunDto(id = 1, status = "completed").toWorkflowRun())
        assertEquals(1L, WorkflowRunDto(id = 1, status = "queued", htmlUrl = "https://x").toWorkflowRun()?.id)
    }
}
