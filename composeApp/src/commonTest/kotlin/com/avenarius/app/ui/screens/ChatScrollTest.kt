package com.avenarius.app.ui.screens

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatScrollTest {
    @Test
    fun aNewMessageUnderAStaleAnchorIsNoPrependOfOlderHistory() {
        // Paging back at the start of the history brings nothing and leaves the anchor on the first message:
        // a poll (or any message) sent next must be followed, not taken for an older page and scrolled to the top.
        assertFalse(olderPrepended(firstKey = "m1", anchorKey = "m1"))
        assertFalse(olderPrepended(firstKey = "m1", anchorKey = null))
        assertTrue(olderPrepended(firstKey = "m0", anchorKey = "m1"), "an older page came in before the anchor")
    }

    @Test
    fun ourOwnSendIsAlwaysFollowedOthersOnlyNearTheBottom() {
        // Scrolled up: only rows 0..10 of 100 visible.
        assertTrue(followAppended(mine = true, lastVisible = 10, prevSize = 100))
        assertFalse(followAppended(mine = false, lastVisible = 10, prevSize = 100))
        assertTrue(followAppended(mine = false, lastVisible = 99, prevSize = 100))
    }
}
