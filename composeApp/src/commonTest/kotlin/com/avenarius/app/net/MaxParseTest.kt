package com.avenarius.app.net

import com.avenarius.app.model.BotButton
import com.avenarius.app.model.ContactCard
import com.avenarius.app.model.Message
import com.avenarius.app.model.TextSpan
import com.avenarius.app.model.canPinMessages
import com.avenarius.app.model.previewLabel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Wire-format parsing of messages and chats ([MaxClient.parseMessage], [MaxClient.parseChat]). */
class MaxParseTest {
    private val client = MaxClient()

    private fun json(s: String): JsonObject = Json.parseToJsonElement(s).jsonObject

    private fun message(s: String): Message? = client.parseMessage(json(s), chatId = 7)

    private fun chat(s: String) = client.parseChat(json(s), emptyMap())

    // --- elements -> spans ---

    @Test
    fun elementsBecomeSpansAndAbsentFromMeansZero() {
        val m =
            message(
                """{"id":"1","sender":2,"time":5,"text":"bold link",
                "elements":[{"type":"STRONG","length":4},
                            {"type":"LINK","from":5,"length":4,"attributes":{"url":"https://x.y"}}]}""",
            )

        assertEquals(
            listOf(
                TextSpan("STRONG", from = 0, length = 4, url = null),
                TextSpan("LINK", from = 5, length = 4, url = "https://x.y"),
            ),
            m?.spans,
        )
    }

    @Test
    fun malformedElementsAreSkippedNotFatal() {
        val m = message("""{"id":"1","text":"abc","elements":[{"type":"STRONG"},{"length":2},"junk",{"type":"EMPHASIZED","length":2}]}""")

        assertEquals(listOf(TextSpan("EMPHASIZED", 0, 2, null)), m?.spans)
    }

    @Test
    fun spansAreDroppedWhenTheTextWasLocalised() {
        // Offsets index the server's placeholder, not the Russian text we show instead.
        val m =
            message(
                """{"id":"1","text":"welcome.saved.dialog.message","elements":[{"type":"STRONG","length":3}]}""",
            )

        assertNotNull(m)
        assertTrue(m.text.startsWith("Добро пожаловать"))
        assertTrue(m.spans.isEmpty())
    }

    // --- edited / reply ---

    @Test
    fun editedStatusSetsEditedFlag() {
        assertTrue(message("""{"id":"1","text":"a","status":"EDITED"}""")!!.edited)
        assertFalse(message("""{"id":"1","text":"a"}""")!!.edited)
        // A numeric status (delivery state) is not an edit.
        assertFalse(message("""{"id":"1","text":"a","status":3}""")!!.edited)
    }

    @Test
    fun replyLinkCarriesQuotedMessageId() {
        val m =
            message(
                """{"id":"2","sender":1,"text":"ответ",
                "link":{"type":"REPLY","message":{"id":"99","sender":5,"text":"вопрос"}}}""",
            )

        val reply = m?.replyTo
        assertNotNull(reply)
        assertEquals("99", reply.messageId)
        assertEquals(5L, reply.senderId)
        assertEquals("вопрос", reply.text)
    }

    // --- inline keyboard ---

    @Test
    fun inlineKeyboardBecomesRowsOfButtons() {
        val m =
            message(
                """{"id":"1","text":"выберите","attaches":[{"_type":"INLINE_KEYBOARD","callbackId":"cb1","keyboard":{"buttons":[
                    [{"text":"Да","type":"CALLBACK","payload":"yes"},{"text":"Сайт","type":"LINK","url":"https://a.b"}],
                    [{"text":"Нет"}],
                    [{"text":""}]
                ]}}]}""",
            )

        assertEquals(
            listOf(
                listOf(
                    BotButton("Да", type = "CALLBACK", payload = "yes", callbackId = "cb1"),
                    BotButton("Сайт", "https://a.b", type = "LINK", callbackId = "cb1"),
                ),
                listOf(BotButton("Нет", callbackId = "cb1")),
            ),
            m?.buttons,
        )
        assertTrue(m!!.buttons[0][0].isCallback)
        assertFalse(m.buttons[0][1].isCallback)
    }

    // --- stickers ---

    @Test
    fun stickerAttachGivesStickerUrlAndIsNotDroppedAsEmpty() {
        // No text, no id: only the sticker keeps this message from being discarded.
        val m = message("""{"sender":2,"time":5,"attaches":[{"_type":"STICKER","url":"https://cdn/s.webp"}]}""")

        assertNotNull(m)
        assertEquals("https://cdn/s.webp", m.stickerUrl)
        assertEquals("Стикер", m.previewLabel())
    }

    @Test
    fun aLottieStickerShowsItsStillImage() {
        val m =
            message(
                """{"sender":2,"time":5,"attaches":[{"_type":"STICKER","stickerType":"LOTTIE",
                "url":"https://cdn/s.webp","lottieUrl":"https://cdn/s.json"}]}""",
            )

        assertEquals("https://cdn/s.webp", m?.stickerUrl)
    }

    @Test
    fun locationAndContactAttachesAreReadAndLabelled() {
        val place = message("""{"id":"1","attaches":[{"_type":"LOCATION","latitude":55.7558,"longitude":37.6173,"zoom":15}]}""")!!
        assertEquals(55.7558, place.location?.latitude)
        assertEquals(37.6173, place.location?.longitude)
        assertEquals("📍 Местоположение", place.previewLabel())

        val user =
            message(
                """{"id":"2","attaches":[{"_type":"CONTACT","contactId":112,"firstName":"Иван","lastName":"Петров","phone":"+79990001122"}]}""",
            )!!
        assertEquals(ContactCard(112, "Иван Петров", "+79990001122"), user.contact)
        assertEquals("👤 Иван Петров", user.previewLabel())

        val card =
            message(
                """{"id":"3","attaches":[{"_type":"CONTACT","vcfBody":"BEGIN:VCARD\nFN:Мама\nTEL;TYPE=CELL:+7 999 000\nEND:VCARD"}]}""",
            )!!
        assertEquals(ContactCard(0, "Мама", "+7 999 000"), card.contact)
    }

    @Test
    fun anEmptyMessageWithoutIdIsDropped() {
        assertNull(message("""{"sender":2,"time":5}"""))
    }

    @Test
    fun aContactWeBlockedIsMarkedSo() {
        assertTrue(parseUser(json("""{"id":5,"names":[{"firstName":"А"}],"status":"BLOCKED"}"""))!!.blocked)
        assertFalse(parseUser(json("""{"id":5,"names":[{"firstName":"А"}]}"""))!!.blocked)
    }

    @Test
    fun ourOwnNameForAContactWinsOverTheirs() {
        // A rename's reply keeps their own name and adds ours as a CUSTOM entry, not necessarily first.
        val u =
            parseUser(
                json(
                    """{"id":5,"names":[{"name":"Василий Петров","firstName":"Василий","lastName":"Петров","type":"ONEME"},
                    {"name":"Вася","firstName":"Вася","type":"CUSTOM"}]}""",
                ),
            )!!
        assertEquals("Вася", u.name)
        assertEquals("Вася", u.firstName)
        assertNull(u.lastName)
        // Without one, the first entry; an entry with only `name` still gives a name.
        assertEquals(
            "Василий Петров",
            parseUser(json("""{"id":5,"names":[{"firstName":"Василий","lastName":"Петров","type":"ONEME"}]}"""))!!.name,
        )
        assertEquals("Алиса AI", parseUser(json("""{"id":5,"names":[{"name":"Алиса AI","type":"NICK"}]}"""))!!.name)
    }

    @Test
    fun strayWhitespaceInANameIsDropped() {
        // A name saved with a trailing space must not show it, re-split the name or change the avatar's colour.
        val u = parseUser(json("""{"id":5,"names":[{"firstName":"Александр ","lastName":" ","type":"CUSTOM"}]}"""))!!
        assertEquals("Александр", u.name)
        assertEquals("Александр", u.firstName)
        assertNull(u.lastName)
    }

    @Test
    fun globalSearchHitsAreReadFromTheChatSearchReply() {
        // CHAT_SEARCH (68): {result: [{chatId, section, count, highlights, message, chat?}], total, marker?}; a hit
        // without a message (a chat or a public channel) is no message hit.
        val hits =
            client.parseMessageHits(
                json(
                    """{"result":[
                    {"chatId":42,"section":"MESSAGES","count":4,"highlights":["Filler"],"message":{"id":"m1","sender":2,"time":5,"text":"Filler 1"}},
                    {"section":"MESSAGES","chat":{"id":43,"type":"CHAT","title":"Группа"},"message":{"id":"m2","sender":2,"time":6,"text":"Filler"}},
                    {"chatId":44,"section":"CHATS","chat":{"id":44,"type":"CHAT","title":"Filler"}}
                    ],"total":3,"marker":"x"}""",
                ),
                chatId = null,
            )
        assertEquals(listOf(42L to "m1", 43L to "m2"), hits.map { it.message.chatId to it.message.id })
        assertEquals("Группа", hits[1].chat?.title)
    }

    // --- per-chat settings (CONFIG op 22): what one chat's entry carries ---

    private fun entry(
        stored: String?,
        key: String,
        value: Long,
    ) = chatSettingsEntry(stored?.let(::json), key, value).toString()

    @Test
    fun pinningAMutedChatKeepsItsMute() {
        assertEquals(
            json("""{"dontDisturbUntil":1781650468681,"sound":true,"vibr":false,"led":true,"favIndex":42}"""),
            json(entry("""{"dontDisturbUntil":1781650468681,"sound":true,"vibr":false,"led":true}""", "favIndex", 42)),
            "a timed mute stays as it was, not turned into -1 or 0",
        )
    }

    @Test
    fun mutingAPinnedChatKeepsItsPin() {
        assertEquals(
            json("""{"dontDisturbUntil":-1,"favIndex":1781650468681,"sound":false,"vibr":false,"led":false}"""),
            json(entry("""{"favIndex":1781650468681,"dontDisturbUntil":0}""", "dontDisturbUntil", -1)),
        )
    }

    @Test
    fun aChatWithNoKnownSettingsGetsTheOfficialFullEntry() {
        // The official builder always writes dontDisturbUntil, sound, vibr and led; favIndex only when set.
        assertEquals(
            json("""{"dontDisturbUntil":-1,"sound":false,"vibr":false,"led":false}"""),
            json(entry(null, "dontDisturbUntil", -1)),
        )
        assertEquals(
            json("""{"dontDisturbUntil":0,"sound":false,"vibr":false,"led":false,"favIndex":0}"""),
            json(entry("""{"favIndex":7}""", "favIndex", 0)),
            "unpinning sends favIndex 0",
        )
    }

    @Test
    fun unknownOrNullKeysAreNotSentBack() {
        assertEquals(
            json("""{"dontDisturbUntil":0,"sound":false,"vibr":false,"led":false,"favIndex":3}"""),
            json(entry("""{"favIndex":null,"other":1,"led":{"x":1}}""", "favIndex", 3)),
        )
    }

    @Test
    fun channelPostViewsAreReadAndPrintedShort() {
        assertEquals(1340, message("""{"id":"1","text":"пост","stats":{"views":1340,"forwards":2}}""")!!.views)
        assertEquals(0, message("""{"id":"1","text":"пост"}""")!!.views)
        assertEquals(
            listOf("999", "1K", "1,3K", "12K", "999K", "1,2M"),
            listOf(999, 1000, 1340, 12_500, 999_999, 1_250_000).map {
                com.avenarius.app.ui.screens
                    .formatViews(it)
            },
        )
    }

    @Test
    fun pollAttachCarriesAnswersTalliesAndOurVote() {
        val m =
            message(
                """{"id":"1","sender":2,"time":5,"attaches":[{"_type":"POLL","pollId":900,"version":1,"title":"Обед?","settings":2,
                "answers":[{"answerId":1,"text":"Да"},{"answerId":2,"text":"Нет"}],
                "state":{"total":3,"result":[{"answerId":1,"voteCount":2,"options":1,"rate":66}],"voterPreviewIds":[1]}}]}""",
            )!!

        val poll = m.poll!!
        assertEquals(900L, poll.pollId)
        assertTrue(poll.multiple)
        assertEquals(3, poll.total)
        assertEquals(listOf(2, 0), poll.answers.map { it.votes })
        assertEquals(listOf(true, false), poll.answers.map { it.mine })
        assertEquals("📊 Обед?", m.previewLabel())
    }

    // --- chats ---

    private fun group(options: String) =
        chat(
            """{"id":-5,"type":"CHAT","title":"Группа","ownerId":555,"options":$options,
            "participants":{"555":1,"0":2}}""",
        )!!

    @Test
    fun optionFlagsSetToFalseDoNotCount() {
        // Regression: every known flag is listed, so presence of the key means nothing.
        assertTrue(group("""{"ONLY_ADMIN_CAN_ADD_MEMBER":false,"OFFICIAL":false}""").canAddMembers)
        assertFalse(group("""{"ONLY_ADMIN_CAN_ADD_MEMBER":false,"OFFICIAL":false}""").official)
    }

    @Test
    fun adminOnlyAddFlagRestrictsRegularMembers() {
        assertFalse(group("""{"ONLY_ADMIN_CAN_ADD_MEMBER":true}""").canAddMembers)
    }

    @Test
    fun adminOnlyAddFlagStillAllowsAdmins() {
        val c =
            chat(
                """{"id":-5,"type":"CHAT","ownerId":555,"adminParticipants":{"0":{}},
                "options":{"ONLY_ADMIN_CAN_ADD_MEMBER":true}}""",
            )!!

        assertTrue(c.canAddMembers)
    }

    @Test
    fun pinRightsFollowRoleAndTheAllCanPinOption() {
        // We are user 0 (MaxClient's myId before a sync); 555 owns the group.
        assertFalse(group("""{"ALL_CAN_PIN_MESSAGE":false}""").canPinMessages(myId = 0))
        assertTrue(group("""{"ALL_CAN_PIN_MESSAGE":true}""").canPinMessages(myId = 0))
        assertTrue(group("{}").canPinMessages(myId = 555), "the owner may")
        val channel = chat("""{"id":-6,"type":"CHANNEL","title":"К","owner":555,"options":{"ALL_CAN_PIN_MESSAGE":true}}""")!!
        assertFalse(channel.canPinMessages(myId = 0), "a channel takes admins only")
        val dialog = chat("""{"id":7,"type":"DIALOG","participants":{"7":0,"0":0}}""")!!
        assertFalse(dialog.canPinMessages(myId = 0), "the server refuses pins in a 1:1 dialog")
    }

    @Test
    fun officialOptionMarksTheChat() {
        assertTrue(group("""{"OFFICIAL":true}""").official)
    }

    @Test
    fun optionsAsArrayOfFlagNamesStillWork() {
        val c = group("""["ONLY_ADMIN_CAN_ADD_MEMBER","OFFICIAL"]""")

        assertFalse(c.canAddMembers)
        assertTrue(c.official)
    }

    @Test
    fun descriptionPinnedMessageAndLastMessageFieldsAreParsed() {
        val c =
            chat(
                """{"id":-5,"type":"CHAT","title":"Г","description":"о группе",
                "pinnedMessage":{"id":"p1","sender":9,"time":50,"text":"важно"},
                "lastMessage":{"id":"l1","sender":3,"time":777,"text":"привет"}}""",
            )!!

        assertEquals("о группе", c.description)
        assertEquals("p1", c.pinnedMessage?.id)
        assertEquals("важно", c.pinnedMessage?.text)
        assertEquals(-5L, c.pinnedMessage?.chatId)
        assertEquals(3L, c.lastSenderId)
        assertEquals(777L, c.lastMessageTime)
        assertEquals("привет", c.lastMessageText)
    }

    @Test
    fun missingOptionalChatFieldsGetDefaults() {
        val c = chat("""{"id":-5,"type":"CHAT","title":"Г","ownerId":555}""")!!

        assertNull(c.description)
        assertNull(c.pinnedMessage)
        assertEquals(0L, c.lastSenderId)
        assertEquals(0L, c.lastMessageTime)
        assertFalse(c.official)
    }
}
