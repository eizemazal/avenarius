package com.avenarius.app.net

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Minimal MessagePack codec, just enough for the Max mobile protocol.
 *
 * The mobile transport sends/receives payloads as MessagePack (the websocket
 * transport uses JSON). To keep the rest of the client identical across both
 * transports, this codec converts between MessagePack bytes and kotlinx
 * [JsonElement] trees — so [MaxClient] still works purely with JsonObject.
 *
 * Hand-rolled (no external dependency) to preserve the clean Android↔desktop
 * code sharing. Spec: https://github.com/msgpack/msgpack/blob/master/spec.md
 */
object MsgPack {

    // ----- Encode (JsonElement -> MessagePack bytes) -----

    fun encode(element: JsonElement): ByteArray {
        val out = ByteSink()
        writeElement(out, element)
        return out.toByteArray()
    }

    private fun writeElement(out: ByteSink, el: JsonElement) {
        when (el) {
            is JsonNull -> out.u8(0xc0)
            is JsonObject -> writeMap(out, el)
            is JsonArray -> writeArray(out, el)
            is JsonPrimitive -> writePrimitive(out, el)
        }
    }

    private fun writePrimitive(out: ByteSink, p: JsonPrimitive) {
        // Booleans
        if (p.isString.not()) {
            p.booleanOrNull?.let { out.u8(if (it) 0xc3 else 0xc2); return }
            val l = p.longOrNull
            if (l != null) { writeInt(out, l); return }
            val d = p.doubleOrNull
            if (d != null) { writeDouble(out, d); return }
        }
        writeString(out, p.content)
    }

    private fun writeInt(out: ByteSink, v: Long) {
        when {
            v in 0..0x7f -> out.u8(v.toInt())                       // positive fixint
            v in -32..-1 -> out.u8(v.toInt() and 0xff)              // negative fixint
            v in 0..0xff -> { out.u8(0xcc); out.u8(v.toInt()) }     // uint8
            v in 0..0xffff -> { out.u8(0xcd); out.u16(v.toInt()) }  // uint16
            v in 0..0xffffffffL -> { out.u8(0xce); out.u32(v) }     // uint32
            v in -128..127 -> { out.u8(0xd0); out.u8(v.toInt() and 0xff) } // int8
            v in -32768..32767 -> { out.u8(0xd1); out.u16(v.toInt()) }     // int16
            v in -2147483648..2147483647 -> { out.u8(0xd2); out.u32(v) }   // int32
            else -> { out.u8(0xd3); out.u64(v) }                   // int64
        }
    }

    private fun writeDouble(out: ByteSink, v: Double) {
        out.u8(0xcb)
        out.u64(v.toRawBits())
    }

    private fun writeString(out: ByteSink, s: String) {
        val bytes = s.encodeToByteArray()
        when {
            bytes.size < 32 -> out.u8(0xa0 or bytes.size)
            bytes.size < 256 -> { out.u8(0xd9); out.u8(bytes.size) }
            bytes.size < 65536 -> { out.u8(0xda); out.u16(bytes.size) }
            else -> { out.u8(0xdb); out.u32(bytes.size.toLong()) }
        }
        out.bytes(bytes)
    }

    private fun writeMap(out: ByteSink, obj: JsonObject) {
        val n = obj.size
        when {
            n < 16 -> out.u8(0x80 or n)
            n < 65536 -> { out.u8(0xde); out.u16(n) }
            else -> { out.u8(0xdf); out.u32(n.toLong()) }
        }
        for ((k, v) in obj) {
            writeString(out, k)
            writeElement(out, v)
        }
    }

    private fun writeArray(out: ByteSink, arr: JsonArray) {
        val n = arr.size
        when {
            n < 16 -> out.u8(0x90 or n)
            n < 65536 -> { out.u8(0xdc); out.u16(n) }
            else -> { out.u8(0xdd); out.u32(n.toLong()) }
        }
        for (v in arr) writeElement(out, v)
    }

    // ----- Decode (MessagePack bytes -> JsonElement) -----

    fun decode(bytes: ByteArray): JsonElement {
        val src = ByteSource(bytes)
        return readElement(src)
    }

    private fun readElement(src: ByteSource): JsonElement {
        val b = src.u8()
        return when {
            b <= 0x7f -> JsonPrimitive(b)                          // positive fixint
            b >= 0xe0 -> JsonPrimitive(b - 256)                    // negative fixint
            b in 0x80..0x8f -> readMap(src, b and 0x0f)            // fixmap
            b in 0x90..0x9f -> readArray(src, b and 0x0f)          // fixarray
            b in 0xa0..0xbf -> JsonPrimitive(src.str(b and 0x1f))  // fixstr
            b == 0xc0 -> JsonNull
            b == 0xc2 -> JsonPrimitive(false)
            b == 0xc3 -> JsonPrimitive(true)
            b == 0xc4 -> JsonPrimitive(src.binAsString(src.u8()))           // bin8
            b == 0xc5 -> JsonPrimitive(src.binAsString(src.u16()))          // bin16
            b == 0xc6 -> JsonPrimitive(src.binAsString(src.u32().toInt()))  // bin32
            b == 0xca -> JsonPrimitive(Float.fromBits(src.u32().toInt()).toDouble())
            b == 0xcb -> JsonPrimitive(Double.fromBits(src.u64()))
            b == 0xcc -> JsonPrimitive(src.u8().toLong())                   // uint8
            b == 0xcd -> JsonPrimitive(src.u16().toLong())                  // uint16
            b == 0xce -> JsonPrimitive(src.u32())                           // uint32
            b == 0xcf -> readUInt64(src)                                    // uint64
            b == 0xd0 -> JsonPrimitive(src.u8().toByte().toLong())          // int8
            b == 0xd1 -> JsonPrimitive(src.u16().toShort().toLong())        // int16
            b == 0xd2 -> JsonPrimitive(src.u32().toInt().toLong())          // int32
            b == 0xd3 -> JsonPrimitive(src.u64())                           // int64
            b == 0xd9 -> JsonPrimitive(src.str(src.u8()))                   // str8
            b == 0xda -> JsonPrimitive(src.str(src.u16()))                  // str16
            b == 0xdb -> JsonPrimitive(src.str(src.u32().toInt()))          // str32
            b == 0xde -> readMap(src, src.u16())                           // map16
            b == 0xdf -> readMap(src, src.u32().toInt())                   // map32
            b == 0xdc -> readArray(src, src.u16())                         // array16
            b == 0xdd -> readArray(src, src.u32().toInt())                 // array32
            // Extension types: we don't use their values, but we MUST consume
            // their bytes (1 type byte + data) or the stream desyncs.
            b == 0xd4 -> readExt(src, 1)   // fixext1
            b == 0xd5 -> readExt(src, 2)   // fixext2
            b == 0xd6 -> readExt(src, 4)   // fixext4
            b == 0xd7 -> readExt(src, 8)   // fixext8
            b == 0xd8 -> readExt(src, 16)  // fixext16
            b == 0xc7 -> readExt(src, src.u8())          // ext8
            b == 0xc8 -> readExt(src, src.u16())         // ext16
            b == 0xc9 -> readExt(src, src.u32().toInt()) // ext32
            else -> JsonNull // truly unknown -> null
        }
    }

    /** Reads (and discards) an extension value: a 1-byte type tag + [dataLen] bytes. */
    private fun readExt(src: ByteSource, dataLen: Int): JsonElement {
        src.skip(1 + dataLen)
        return JsonNull
    }

    private fun readUInt64(src: ByteSource): JsonPrimitive {
        val v = src.u64()
        // Preserve values that overflow Long as strings (rumax does the same).
        return if (v < 0) JsonPrimitive(v.toULong().toString()) else JsonPrimitive(v)
    }

    private fun readMap(src: ByteSource, n: Int): JsonObject {
        val map = LinkedHashMap<String, JsonElement>(n)
        repeat(n) {
            val key = when (val k = readElement(src)) {
                is JsonPrimitive -> k.content
                else -> k.toString()
            }
            map[key] = readElement(src)
        }
        return JsonObject(map)
    }

    private fun readArray(src: ByteSource, n: Int): JsonArray {
        val list = ArrayList<JsonElement>(n)
        repeat(n) { list.add(readElement(src)) }
        return JsonArray(list)
    }
}

/** Growable byte buffer for encoding. */
private class ByteSink {
    private var buf = ByteArray(64)
    private var size = 0
    private fun ensure(extra: Int) {
        if (size + extra > buf.size) {
            var n = buf.size * 2
            while (n < size + extra) n *= 2
            buf = buf.copyOf(n)
        }
    }
    fun u8(v: Int) { ensure(1); buf[size++] = v.toByte() }
    fun u16(v: Int) { ensure(2); buf[size++] = (v ushr 8).toByte(); buf[size++] = v.toByte() }
    fun u32(v: Long) { ensure(4); buf[size++] = (v ushr 24).toByte(); buf[size++] = (v ushr 16).toByte(); buf[size++] = (v ushr 8).toByte(); buf[size++] = v.toByte() }
    fun u64(v: Long) { ensure(8); for (s in 56 downTo 0 step 8) buf[size++] = (v ushr s).toByte() }
    fun bytes(b: ByteArray) { ensure(b.size); b.copyInto(buf, size); size += b.size }
    fun toByteArray(): ByteArray = buf.copyOf(size)
}

/** Cursor over a byte array for decoding (big-endian reads). */
private class ByteSource(private val buf: ByteArray) {
    private var pos = 0
    fun u8(): Int = buf[pos++].toInt() and 0xff
    fun u16(): Int = (u8() shl 8) or u8()
    fun u32(): Long = (u8().toLong() shl 24) or (u8().toLong() shl 16) or (u8().toLong() shl 8) or u8().toLong()
    fun u64(): Long {
        var v = 0L
        repeat(8) { v = (v shl 8) or u8().toLong() }
        return v
    }
    fun str(len: Int): String {
        val s = buf.decodeToString(pos, pos + len)
        pos += len
        return s
    }
    fun binAsString(len: Int): String = str(len)
    fun skip(n: Int) { pos += n }
}
