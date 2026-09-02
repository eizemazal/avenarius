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
        assertEquals("joinABC123", link.token)
        assertEquals("max.ru/joinABC123", link.link)
        assertEquals("https://max.ru/joinABC123", link.url)
    }

    @Test
    fun theHashOfAJoinLinkIsTheSegmentAfterJoin() {
        // What the protocol wants: a single path segment, not the whole link.
        val link = parseMaxLink("https://max.ru/join/SOMEHASH")
        assertTrue(link is MaxLink.Invite)
        assertEquals("SOMEHASH", link.token)
        assertEquals("https://max.ru/join/SOMEHASH", link.url)
    }

    @Test
    fun schemeAndWwwAndQueryDoNotMatter() {
        // The token and host/path are what the server sees, so those must agree
        // however the link was written.
        val forms =
            listOf(
                "https://max.ru/abc",
                "http://max.ru/abc",
                "https://www.max.ru/abc",
                "https://max.ru/abc?utm=1",
                "https://max.ru/abc#top",
                "  https://max.ru/abc  ",
                "https://MAX.RU/abc",
            )
        for (form in forms) {
            val link = parseMaxLink(form)
            assertTrue(link is MaxLink.Invite, form)
            assertEquals("abc", link.token, form)
            assertEquals("max.ru/abc", link.link, form)
        }
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
