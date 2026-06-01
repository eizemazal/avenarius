package com.avenarius.app.net

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The Max "mobile" transport: a raw TLS connection carrying length-prefixed,
 * MessagePack-encoded, optionally LZ4-compressed frames.
 *
 * Frame = 10-byte big-endian header + payload:
 *   [ver:u8][cmd:u16][seq:u8][opcode:u16][packedLen:u32]
 * where packedLen's top byte is a compression flag and the low 24 bits are the
 * payload length. Requests are sent uncompressed; responses may be LZ4-compressed.
 *
 * Lives in commonMain — the only platform-specific dependency is [TlsSocket].
 */
class MobileTransport(
    private val host: String = "api.oneme.ru",
    private val port: Int = 443,
) {
    private companion object {
        // Packet types (the high byte of the header's cmd field).
        const val PACKET_RESPONSE = 1
        const val PACKET_ERROR = 3
    }

    private val socket = TlsSocket(host, port)
    // A stray throwable in a background coroutine must never crash the app, so the
    // scope swallows uncaught exceptions (each coroutine also handles its own).
    private val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })
    private val writeLock = Mutex()
    private val pendingLock = Mutex()

    private var seq = 0
    private val pending = mutableMapOf<Int, CompletableDeferred<JsonObject>>()

    private var readerJob: Job? = null
    private var pingJob: Job? = null
    private var connected = false

    /** Server-pushed frames as (opcode, payload). */
    private val _events = MutableSharedFlow<Pair<Int, JsonObject>>(extraBufferCapacity = 64)
    val events: SharedFlow<Pair<Int, JsonObject>> = _events

    val isConnected: Boolean get() = connected

    suspend fun connect() {
        if (connected) return
        socket.connect()
        seq = 0 // fresh connection -> fresh seq sequence
        connected = true
        readerJob = scope.launch { readLoop() }
    }

    fun startPing(intervalMs: Long = 30_000) {
        if (pingJob != null) return
        pingJob = scope.launch {
            while (isActive) {
                delay(intervalMs)
                runCatching { request(1, buildJsonObject { put("interactive", true) }) }
            }
        }
    }

    fun disconnect() {
        connected = false
        pingJob?.cancel(); pingJob = null
        readerJob?.cancel(); readerJob = null
        socket.close()
        failAllPending(IllegalStateException("Соединение закрыто"))
    }

    /** Sends a request and suspends until the reply with the same seq arrives. */
    suspend fun request(opcode: Int, payload: JsonObject, timeoutMs: Long = 30_000): JsonObject {
        if (!connected) error("Нет соединения с сервером")
        val deferred = CompletableDeferred<JsonObject>()
        writeLock.withLock {
            seq = (seq + 1) and 0xff
            val mySeq = seq
            pendingLock.withLock { pending[mySeq] = deferred }
            socket.write(encodeFrame(ver = 11, cmd = 0, seq = mySeq, opcode = opcode, payload = payload))
        }
        return withTimeout(timeoutMs) { deferred.await() }
    }

    private suspend fun readLoop() {
        val header = ByteArray(10)
        while (connected) {
            // --- read one whole frame off the wire ---
            // I/O errors here mean the socket is dead -> end the connection.
            var packetType = 0; var frameSeq = 0; var opcode = 0; var compFlag = 0
            var body = ByteArray(0)
            try {
                socket.readFully(header)
                // header[1..2] is a big-endian packet-type field; the meaningful byte
                // is the high one: 0=request/push, 1=response, 2=event, 3=error.
                packetType = header[1].toInt() and 0xff
                frameSeq = header[3].toInt() and 0xff
                opcode = ((header[4].toInt() and 0xff) shl 8) or (header[5].toInt() and 0xff)
                val packed = ((header[6].toInt() and 0xff) shl 24) or ((header[7].toInt() and 0xff) shl 16) or
                    ((header[8].toInt() and 0xff) shl 8) or (header[9].toInt() and 0xff)
                compFlag = packed ushr 24
                val len = packed and 0xFFFFFF
                if (len > 0) {
                    body = ByteArray(len)
                    socket.readFully(body)
                }
            } catch (e: Throwable) {
                connected = false
                failAllPending(e)
                break
            }

            // --- decode + route ---
            // A decode failure means one bad frame, NOT a dead connection (we
            // already consumed its bytes, so the stream stays aligned) — skip it.
            try {
                val raw = if (compFlag != 0 && body.isNotEmpty()) Lz4.decompressBlock(body) else body
                val payload = if (raw.isNotEmpty()) MsgPack.decode(raw) as? JsonObject ?: JsonObject(emptyMap())
                else JsonObject(emptyMap())

                // Only RESPONSE/ERROR frames are replies to one of our requests and
                // may be matched by seq. Pushes (request/event type) carry their OWN
                // seq that can collide with ours, so they must go straight to events.
                val isReply = packetType == PACKET_RESPONSE || packetType == PACKET_ERROR
                if (!isReply) {
                    _events.emit(opcode to payload)
                } else {
                    val waiter = pendingLock.withLock { pending.remove(frameSeq) }
                    if (waiter != null && !waiter.isCompleted) waiter.complete(payload)
                    else _events.emit(opcode to payload)
                }
            } catch (_: Throwable) {
                // skip the unparseable frame, keep the connection alive
            }
        }
    }

    private fun failAllPending(cause: Throwable) {
        scope.launch {
            pendingLock.withLock {
                pending.values.forEach { if (!it.isCompleted) it.completeExceptionally(cause) }
                pending.clear()
            }
        }
    }

    private fun encodeFrame(ver: Int, cmd: Int, seq: Int, opcode: Int, payload: JsonObject): ByteArray {
        val body = MsgPack.encode(payload)
        val frame = ByteArray(10 + body.size)
        frame[0] = ver.toByte()
        frame[1] = (cmd ushr 8).toByte(); frame[2] = cmd.toByte()
        frame[3] = seq.toByte()
        frame[4] = (opcode ushr 8).toByte(); frame[5] = opcode.toByte()
        val len = body.size // requests are uncompressed -> top byte 0
        frame[6] = (len ushr 24).toByte(); frame[7] = (len ushr 16).toByte()
        frame[8] = (len ushr 8).toByte(); frame[9] = len.toByte()
        body.copyInto(frame, 10)
        return frame
    }
}
