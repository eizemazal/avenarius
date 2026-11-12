package com.avenarius.app.net

import com.avenarius.app.model.Sticker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

/** The sticker panel's replies: ASSETS_UPDATE sections and ASSETS_GET_BY_IDS sets / stickers. */
class StickersTest {
    private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

    @Test
    fun sectionsGiveSetIdsInOrderAndTheRecents() {
        val (sets, recent) =
            parseStickerSections(
                json(
                    """{"sync":5,"sections":[
                    {"id":1,"type":"STICKER_SETS","title":"Мои","stickerSets":[30,10]},
                    {"id":2,"type":"RECENTS","stickers":[7,8]},
                    {"id":3,"type":"STICKER_SETS","stickerSets":[10,20]}],
                    "recentsList":[8,{"id":9}]}""",
                ),
            )

        assertEquals(listOf(30L, 10L, 20L), sets)
        assertEquals(listOf(7L, 8L, 9L), recent)
    }

    @Test
    fun setsAndStickersParseAndStickersWithoutAnImageAreLeftOut() {
        val sets =
            parseStickerSets(
                json("""{"stickerSets":[{"id":10,"name":"Коты","iconUrl":"https://i/10","stickers":[1,2]},{"name":"без id"}]}"""),
            )
        assertEquals(1, sets.size)
        assertEquals(listOf(1L, 2L), sets.single().stickerIds)

        val stickers =
            parseStickers(
                json(
                    """{"stickers":[{"id":1,"url":"https://s/1.webp","lottieUrl":"https://s/1.json","type":"LOTTIE"},{"id":2,"url":""},{"id":3}]}""",
                ),
            )
        assertEquals(listOf(Sticker(1, "https://s/1.webp")), stickers)
    }
}
