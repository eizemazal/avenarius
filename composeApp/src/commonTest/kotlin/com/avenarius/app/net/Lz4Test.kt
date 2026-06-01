package com.avenarius.app.net

import kotlin.test.Test
import kotlin.test.assertEquals

class Lz4Test {
    private fun decode(vararg bytes: Int): String = Lz4.decompressBlock(bytes.map { it.toByte() }.toByteArray()).decodeToString()

    @Test
    fun literalsOnly() {
        // token high nibble = literal length (4), then the 4 literal bytes "ABCD".
        assertEquals("ABCD", decode(0x40, 'A'.code, 'B'.code, 'C'.code, 'D'.code))
    }

    @Test
    fun overlappingMatch() {
        // token 0x10: 1 literal 'A', match-len nibble 0 (=> 4). Then offset=1 (LE u16).
        // Copies 'A' back-referenced 4 times -> "AAAAA" (overlap must work byte-by-byte).
        assertEquals("AAAAA", decode(0x10, 'A'.code, 0x01, 0x00))
    }

    @Test
    fun extendedLiteralLength() {
        // litLen nibble = 15 -> extra length byte(s). 15 + 5 = 20 literal 'X'.
        val bytes = intArrayOf(0xF0, 0x05) + IntArray(20) { 'X'.code }
        assertEquals("X".repeat(20), Lz4.decompressBlock(bytes.map { it.toByte() }.toByteArray()).decodeToString())
    }

    @Test
    fun emptyBlock() {
        assertEquals("", Lz4.decompressBlock(ByteArray(0)).decodeToString())
    }
}
