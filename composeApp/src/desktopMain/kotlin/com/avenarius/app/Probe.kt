package com.avenarius.app.debug

import com.avenarius.app.net.Lz4
import com.avenarius.app.net.MsgPack
import com.avenarius.app.net.TlsSocket
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.system.exitProcess

/**
 * Mobile-protocol probe: TLS to api.oneme.ru:443, binary framing, MessagePack
 * payloads, LZ4-compressed responses. Validates the transport end-to-end with a
 * FAKE phone number (no real SMS).
 */
fun main(): Unit =
    runBlocking {
        val sock = TlsSocket("api.oneme.ru", 443)
        sock.connect()
        println("== TLS connected ==")

        var seq = 0

        suspend fun send(
            opcode: Int,
            payload: JsonObject,
        ) {
            val body = MsgPack.encode(payload)
            val header = ByteArray(10)
            header[0] = 11 // ver
            header[1] = 0
            header[2] = 0 // cmd (u16 BE)
            header[3] = seq.toByte() // seq
            header[4] = (opcode ushr 8).toByte()
            header[5] = opcode.toByte() // opcode u16 BE
            val len = body.size
            header[6] = (len ushr 24).toByte()
            header[7] = (len ushr 16).toByte()
            header[8] = (len ushr 8).toByte()
            header[9] = len.toByte() // len u32 BE (comp flag 0)
            sock.write(header + body)
            println(">>> op=$opcode seq=$seq payload=$payload")
        }

        suspend fun recv(label: String): JsonElement {
            val header = ByteArray(10)
            sock.readFully(header)
            val opcode = ((header[4].toInt() and 0xff) shl 8) or (header[5].toInt() and 0xff)
            val packed =
                ((header[6].toInt() and 0xff) shl 24) or ((header[7].toInt() and 0xff) shl 16) or
                    ((header[8].toInt() and 0xff) shl 8) or (header[9].toInt() and 0xff)
            val comp = packed ushr 24
            val len = packed and 0xFFFFFF
            val body = ByteArray(len)
            if (len > 0) sock.readFully(body)
            val raw = if (comp != 0) Lz4.decompressBlock(body) else body
            val json = if (len > 0) MsgPack.decode(raw) else JsonObject(emptyMap())
            println("<<< [$label] op=$opcode comp=$comp len=$len $json")
            return json
        }

        val deviceId = "11111111-2222-3333-4444-555555555555"
        seq = 0
        send(
            6,
            buildJsonObject {
                put("clientSessionId", 1)
                put("mt_instanceid", "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                putJsonObject("userAgent") {
                    put("deviceType", "ANDROID")
                    put("appVersion", "26.17.0")
                    put("osVersion", "Android 13")
                    put("timezone", "Europe/Moscow")
                    put("screen", "130dpi 130dpi 600x874")
                    put("pushDeviceType", "GCM")
                    put("locale", "ru")
                    put("buildNumber", 6713)
                    put("deviceName", "unknown Generic Android-x86_64")
                    put("deviceLocale", "ru")
                }
                put("deviceId", deviceId)
            },
        )
        recv("handshake")

        seq = 1
        send(
            17,
            buildJsonObject {
                put("phone", "+79990000000")
                put("type", "START_AUTH")
                put("language", "ru")
            },
        )
        recv("start_auth")

        sock.close()
        println("== done ==")
        exitProcess(0)
    }
