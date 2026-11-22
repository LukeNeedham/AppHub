package com.lukeneedham.apphub

import com.lukeneedham.apphub.domain.normalizeProductionUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductionUrlTest {
    @Test
    fun `a full web address is kept`() {
        assertEquals("https://www.lukeneedham.com/", normalizeProductionUrl(" https://www.lukeneedham.com/ "))
        assertEquals("http://example.com", normalizeProductionUrl("http://example.com"))
    }

    @Test
    fun `an address with no scheme gets https`() {
        assertEquals("https://example.com", normalizeProductionUrl("example.com"))
    }

    @Test
    fun `nothing, blanks and other schemes are no url`() {
        assertNull(normalizeProductionUrl(null))
        assertNull(normalizeProductionUrl("  "))
        assertNull(normalizeProductionUrl("javascript://alert(1)"))
        assertNull(normalizeProductionUrl("ftp://example.com"))
        assertNull(normalizeProductionUrl("not a url"))
        assertNull(normalizeProductionUrl("https://"))
    }
}
