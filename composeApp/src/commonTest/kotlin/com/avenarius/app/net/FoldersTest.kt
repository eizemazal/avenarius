package com.avenarius.app.net

import com.avenarius.app.model.Chat
import com.avenarius.app.model.ChatFolder
import com.avenarius.app.model.matches
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Folder replies and pushes, how they merge, and which chats a folder shows. */
class FoldersTest {
    private fun parse(s: String) = parseFolders(Json.parseToJsonElement(s).jsonObject)

    @Test
    fun aFoldersGetReplyKeepsFiltersAndOptionsAsSent() {
        val u =
            parse(
                """{"foldersOrder":["all.chat.folder","x"],"folderSync":5,"folders":[
                {"id":"all.chat.folder","title":"Все","filters":[],"options":[]},
                {"id":"x","title":"Сферум","filters":[13],"include":[51,6],"options":[2,3,1],"favorites":[6],"updateTime":1}]}""",
            )

        assertEquals(listOf("all.chat.folder", "x"), u.order)
        val x = u.folders[1]
        assertEquals(listOf(51L, 6L), x.include)
        assertEquals(listOf(6L), x.favorites)
        assertEquals(listOf(13), x.filterIds)
        assertEquals("[2,3,1]", x.options.toString())
    }

    @Test
    fun aPushUpsertsOneFolderAndItsOrderDropsDeletedOnes() {
        val current = listOf(ChatFolder("all.chat.folder", "Все"), ChatFolder("a", "А"), ChatFolder("b", "Б"))

        val renamed = current.applying(parse("""{"folder":{"id":"a","title":"А2"},"folderSync":9}"""))
        assertEquals(listOf("Все", "А2", "Б"), renamed.map { it.title })

        val deleted = current.applying(parse("""{"foldersOrder":["all.chat.folder","b"],"folderSync":9}"""))
        assertEquals(listOf("all.chat.folder", "b"), deleted.map { it.id })
    }

    @Test
    fun theRawOrderKeepsSystemFoldersSentAsAnIdAlone() {
        // As in the sample reply: three ids in the order, one folder object.
        val u = parse("""{"foldersOrder":["all.chat.folder","x","chat.channel.folder"],"folders":[{"id":"x","title":"Новые"}]}""")
        val order = emptyList<String>().applyingOrder(u)

        assertEquals(listOf("all.chat.folder", "x", "chat.channel.folder"), order)
        assertEquals(listOf("x"), emptyList<ChatFolder>().applying(u).map { it.id })
        // A push without an order keeps it and adds a new folder at the end.
        assertEquals(order + "y", order.applyingOrder(parse("""{"folder":{"id":"y","title":"Y"}}""")))
    }

    @Test
    fun swappingTwoFoldersLeavesEveryOtherIdInPlace() {
        val order = listOf("all.chat.folder", "a", "chat.channel.folder", "b")

        assertEquals(listOf("all.chat.folder", "b", "chat.channel.folder", "a"), order.swapping("a", "b"))
        // An id the order lacks (a folder just created here) joins it first.
        assertEquals(listOf("all.chat.folder", "n", "chat.channel.folder", "b", "a"), order.swapping("a", "n"))
    }

    @Test
    fun aFolderWithoutAnIdIsSkipped() {
        val u = parse("""{"folders":[{"title":"?"},{"id":"","title":"?"},"junk"]}""")

        assertTrue(u.folders.isEmpty())
        assertNull(u.order)
    }

    private val dialog = Chat(id = 1, title = "Аня", lastMessageText = null, lastEventTime = 1, isDialog = true)
    private val group = Chat(id = -2, title = "Группа", lastMessageText = null, lastEventTime = 1, unreadCount = 3)
    private val channel = Chat(id = -3, title = "Канал", lastMessageText = null, lastEventTime = 1, isChannel = true, muted = true)

    private fun folder(
        vararg filters: Int,
        include: List<Long> = emptyList(),
    ) = ChatFolder("f", "П", include = include, filters = JsonArray(filters.map { JsonPrimitive(it) }))

    @Test
    fun kindFiltersMatchAnyAndStateFiltersNarrowThem() {
        assertEquals(listOf(channel), listOf(dialog, group, channel).filter { folder(2).matches(it, 100) })
        // Dialogs or groups…
        assertEquals(listOf(dialog, group), listOf(dialog, group, channel).filter { folder(4, 3).matches(it, 100) })
        // …that are unread.
        assertEquals(listOf(group), listOf(dialog, group, channel).filter { folder(4, 3, 0).matches(it, 100) })
    }

    @Test
    fun includedChatsAlwaysMatchAndAnEmptyFolderHoldsOnlyThem() {
        assertTrue(folder(2, include = listOf(1)).matches(dialog, 100))
        assertFalse(folder().matches(group, 100))
        assertTrue(ChatFolder(ChatFolder.ALL_ID, "Все").matches(group, 100))
    }

    @Test
    fun filterNamesSpelledOutAreUnderstood() {
        val f = ChatFolder("f", "П", filters = JsonArray(listOf(JsonPrimitive("CHANNEL"))))

        assertEquals(listOf(2), f.filterIds)
        assertTrue(f.matches(channel, 100))
    }
}
