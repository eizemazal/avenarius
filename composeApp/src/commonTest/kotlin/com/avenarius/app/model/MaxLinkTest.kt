package com.avenarius.app.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MaxLinkTest {
    @Test
    fun inviteLinksAreRecognised() {
        val link = parseMaxLink("https://max.ru/joinABC123")
        assertTrue(link is MaxLink.Invite)
        assertEquals("max.ru/joinABC123", link.link)
        assertEquals("joinABC123", link.token)
    }

    @Test
    fun schemeAndWwwAndQueryDoNotMatter() {
        val expected = parseMaxLink("https://max.ru/abc")
        assertEquals(expected, parseMaxLink("http://max.ru/abc"))
        assertEquals(expected, parseMaxLink("https://www.max.ru/abc"))
        assertEquals(expected, parseMaxLink("https://max.ru/abc?utm=1"))
        assertEquals(expected, parseMaxLink("https://max.ru/abc#top"))
        assertEquals(expected, parseMaxLink("  https://max.ru/abc  "))
        assertEquals(expected, parseMaxLink("https://MAX.RU/abc"))
    }

    @Test
    fun subdomainsAndOnemeCount() {
        assertTrue(parseMaxLink("https://go.max.ru/abc") is MaxLink.Invite)
        assertTrue(parseMaxLink("https://oneme.ru/abc") is MaxLink.Invite)
    }

    @Test
    fun callLinksAreTheirOwnKind() {
        val link = parseMaxLink("https://max.ru/joincall/xyz-42")
        assertEquals(MaxLink.JoinCall("xyz-42"), link)
    }

    @Test
    fun otherHostsAreLeftToTheBrowser() {
        assertNull(parseMaxLink("https://example.com/abc"))
        assertNull(parseMaxLink("https://notmax.ru/abc"))
        assertNull(parseMaxLink("https://max.ru.evil.com/abc"), "a look-alike host must not match")
        assertNull(parseMaxLink("mailto:someone@max.ru"))
    }

    @Test
    fun barePagesAreNotInvites() {
        // Nothing to open: the site's front page, not a chat.
        assertNull(parseMaxLink("https://max.ru"))
        assertNull(parseMaxLink("https://max.ru/"))
    }

    @Test
    fun deeperPathsUseTheLastSegmentAsTheToken() {
        val link = parseMaxLink("https://max.ru/g/abc123")
        assertTrue(link is MaxLink.Invite)
        assertEquals("abc123", link.token)
        assertEquals("max.ru/g/abc123", link.link)
    }
}
