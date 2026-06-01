package com.avenarius.app.net

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MsgPackTest {
    private fun roundTrip(obj: JsonObject) {
        val decoded = MsgPack.decode(MsgPack.encode(obj))
        assertEquals(obj, decoded, "round-trip mismatch")
    }

    @Test
    fun roundTripsScalars() {
        roundTrip(
            buildJsonObject {
                put("nil", null as String?)
                put("t", true)
                put("f", false)
                put("zero", 0)
                put("small", 42)
                put("neg", -1)
                put("negfix", -30)
                put("u8", 200)
                put("u16", 50_000)
                put("u32", 3_000_000_000L)
                put("i16", -1000)
                put("i32", -100_000)
                put("i64", -5_000_000_000L)
                put("str", "привет 👋 mixed ascii")
                put("empty", "")
                put("dbl", 3.5)
            },
        )
    }

    @Test
    fun roundTripsNestedAndArrays() {
        roundTrip(
            buildJsonObject {
                putJsonObject("user") {
                    put("id", 293679916L)
                    put("name", "Алиса")
                }
                putJsonArray("nums") { repeat(20) { add(JsonPrimitive(it)) } } // forces array16 path
                putJsonArray("strs") {
                    add(JsonPrimitive("a"))
                    add(JsonPrimitive("b"))
                }
            },
        )
    }

    @Test
    fun roundTripsLargeMap() {
        // >15 entries forces map16 instead of fixmap.
        roundTrip(buildJsonObject { repeat(40) { put("k$it", it) } })
    }

    @Test
    fun decodesKnownBytes() {
        // fixmap{1} "id" -> 7 :  0x81 0xa2 'i' 'd' 0x07
        val bytes = byteArrayOf(0x81.toByte(), 0xA2.toByte(), 'i'.code.toByte(), 'd'.code.toByte(), 0x07)
        val obj = MsgPack.decode(bytes).jsonObject
        assertEquals(JsonPrimitive(7L), obj["id"])
    }

    @Test
    fun skipsExtTypesWithoutDesync() {
        // {"a": fixext1(type=5,data=0x09), "b": 1}
        // fixext1 = 0xd4, then type byte, then 1 data byte. Must be consumed so "b" parses.
        val bytes =
            byteArrayOf(
                0x82.toByte(), // fixmap size 2
                0xA1.toByte(),
                'a'.code.toByte(), // "a"
                0xD4.toByte(),
                0x05,
                0x09, // fixext1 type=5 data=9 -> JsonNull
                0xA1.toByte(),
                'b'.code.toByte(), // "b"
                0x01, // 1
            )
        val obj = MsgPack.decode(bytes).jsonObject
        assertEquals(JsonPrimitive(1L), obj["b"], "field after ext must still parse")
        assertTrue(obj.containsKey("a"))
    }
}
