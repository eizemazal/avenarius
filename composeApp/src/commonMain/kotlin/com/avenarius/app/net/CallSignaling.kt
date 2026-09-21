package com.avenarius.app.net

import com.avenarius.app.model.CallSetup
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * STAGE 2 of a call: the WebRTC signaling channel to the SFU (videowebrtc.okcdn.ru).
 *
 * The official clients use WebTransport; we use the equivalent **ws2 WebSocket**
 * endpoint (same JSON command protocol) because Android has no WebTransport client.
 * Wire format (reverse-engineered — see the "max-calls-feasibility" note):
 *   client -> server: {"command": <name>, "sequence": <int>, ...params}
 *   server -> client: {"type":"notification", "notification": <kind>, ...}
 * SDP offer/answer and trickle ICE ride "transmit-data" / "transmitted-data".
 *
 * commonMain (Ktor WebSocket). The platform CallEngine drives it: it hands us SDP
 * and ICE to [sendSdp]/[sendCandidate] and consumes [remoteSdp]/[remoteCandidate].
 */
class CallSignaling(
    private val setup: CallSetup,
    /** `start` for the caller, `accept` for the callee — appended to the ws2 URL. */
    private val tgt: String = "start",
    private val http: HttpClient = createHttpClient(),
) {
    private val scope = CoroutineScope(SupervisorJob())
    private var session: io.ktor.client.plugins.websocket.DefaultClientWebSocketSession? = null
    private var receiveJob: Job? = null
    private val seqLock = Mutex()
    private var sequence = 0

    private val _remoteSdp = MutableSharedFlow<RemoteSdp>(extraBufferCapacity = 8)
    val remoteSdp: SharedFlow<RemoteSdp> = _remoteSdp

    private val _remoteCandidate = MutableSharedFlow<RemoteCandidate>(extraBufferCapacity = 64)
    val remoteCandidate: SharedFlow<RemoteCandidate> = _remoteCandidate

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SignalingEvent> = _events

    /** SDP offer/answer relayed by the SFU. */
    data class RemoteSdp(
        val type: String,
        val sdp: String,
        val participantId: Long,
    )

    /** A trickle ICE candidate relayed by the SFU. */
    data class RemoteCandidate(
        val candidate: String,
        val sdpMid: String?,
        val sdpMLineIndex: Int?,
        val participantId: Long,
    )

    /** High-level signaling notifications the call session reacts to. */
    sealed interface SignalingEvent {
        /** Join acknowledged; may carry refreshed ICE servers. */
        data class Connected(
            val raw: JsonObject,
        ) : SignalingEvent

        /** The remote peer accepted the call. */
        data class Accepted(
            val participantId: Long,
        ) : SignalingEvent

        /** A peer registered/joined the conversation. */
        data class PeerRegistered(
            val participantId: Long,
        ) : SignalingEvent

        /** The remote peer's mic/camera state changed. */
        data class MediaSettingsChanged(
            val participantId: Long,
            val raw: JsonObject,
        ) : SignalingEvent

        /** The call ended on the SFU side. */
        data class Hungup(
            val reason: String?,
        ) : SignalingEvent

        /** Any other notification, exposed raw for diagnostics. */
        data class Other(
            val kind: String,
            val raw: JsonObject,
        ) : SignalingEvent
    }

    /** Opens the ws2 WebSocket and starts the receive loop. */
    suspend fun connect() {
        val url = withRequiredParams(setup.wsEndpoint, tgt)
        clog("ws2 url: ${url.take(140)}")
        val s = http.webSocketSession(urlString = url)
        session = s
        receiveJob =
            scope.launch {
                try {
                    s.incoming.consumeEach { frame ->
                        val text =
                            when (frame) {
                                is Frame.Text -> frame.readText()
                                is Frame.Binary -> frame.data.decodeToString()
                                else -> return@consumeEach
                            }
                        val trimmed = text.trim()
                        // Heartbeat: the SFU sends a bare `ping`; we MUST reply `pong` or the
                        // server's connection "doctor" tears the socket down within seconds.
                        if (trimmed == "ping") {
                            runCatching { s.send("pong") }
                            return@consumeEach
                        }
                        if (!trimmed.startsWith("{")) {
                            clog("ws2 RECV (non-json): $trimmed")
                            return@consumeEach
                        }
                        clog("ws2 RECV: ${text.take(200)}")
                        runCatching { route(Json.parseToJsonElement(text).jsonObject) }
                            .onFailure { clog("ws2 route parse error: $it") }
                    }
                    clog("ws2 incoming closed")
                } catch (t: Throwable) {
                    // socket closed / errored -> treat as hangup
                    clog("ws2 receive error: $t")
                    _events.tryEmit(SignalingEvent.Hungup("connection lost"))
                }
            }
    }

    private suspend fun route(msg: JsonObject) {
        val kind = msg["notification"]?.jsonPrimitive?.contentOrNull ?: msg["type"]?.jsonPrimitive?.contentOrNull
        val participantId = msg["participantId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L
        when (kind) {
            "transmitted-data" -> {
                parseTransmittedSdp(msg)?.let { _remoteSdp.emit(it) }
                parseTransmittedCandidate(msg)?.let { _remoteCandidate.emit(it) }
            }
            "connection" -> _events.emit(SignalingEvent.Connected(msg))
            "accepted-call" -> _events.emit(SignalingEvent.Accepted(participantId))
            "registered-peer", "participant-added", "participant-joined" ->
                _events.emit(SignalingEvent.PeerRegistered(participantId))
            "media-settings-changed" -> _events.emit(SignalingEvent.MediaSettingsChanged(participantId, msg))
            "hungup", "closed-conversation" ->
                _events.emit(SignalingEvent.Hungup(msg["reason"]?.jsonPrimitive?.contentOrNull))
            "response" -> {
                // The accept-call response carries the remote participant id(s).
                if (msg["response"]?.jsonPrimitive?.contentOrNull == "accept-call") {
                    msg["participantIds"]
                        ?.let { it as? kotlinx.serialization.json.JsonArray }
                        ?.firstOrNull()
                        ?.jsonPrimitive
                        ?.contentOrNull
                        ?.toLongOrNull()
                        ?.let { _events.emit(SignalingEvent.PeerRegistered(it)) }
                }
            }
            else -> if (kind != null) _events.emit(SignalingEvent.Other(kind, msg))
        }
    }

    /** Sends our local SDP (offer or answer) to [participantId] via `transmit-data`. */
    suspend fun sendSdp(
        participantId: Long,
        type: String,
        sdp: String,
    ) = sendCommand(CMD_TRANSMIT_DATA) {
        put("participantId", participantId)
        putJsonObject("data") {
            putJsonObject("sdp") {
                put("type", type)
                put("sdp", sdp)
            }
        }
    }

    /** Sends a local trickle ICE candidate to [participantId] via `transmit-data`. */
    suspend fun sendCandidate(
        participantId: Long,
        candidate: String,
        sdpMid: String?,
        sdpMLineIndex: Int?,
        usernameFragment: String? = null,
    ) = sendCommand(CMD_TRANSMIT_DATA) {
        put("participantId", participantId)
        put("participantType", "USER")
        putJsonObject("data") {
            putJsonObject("candidate") {
                put("candidate", candidate)
                sdpMid?.let { put("sdpMid", it) }
                sdpMLineIndex?.let { put("sdpMLineIndex", it) }
                usernameFragment?.let { put("usernameFragment", it) }
            }
        }
    }

    /** Accepts an inbound call (callee side), then announces our media state. */
    suspend fun acceptCall(
        micEnabled: Boolean,
        cameraEnabled: Boolean,
    ) {
        sendCommand(CMD_ACCEPT_CALL) { putMediaSettings(micEnabled, cameraEnabled) }
        sendCommand(CMD_CHANGE_MEDIA_SETTINGS) { putMediaSettings(micEnabled, cameraEnabled) }
    }

    /** Tells the SFU our mic/camera changed. */
    suspend fun changeMediaSettings(
        micEnabled: Boolean,
        cameraEnabled: Boolean,
    ) = sendCommand(CMD_CHANGE_MEDIA_SETTINGS) { putMediaSettings(micEnabled, cameraEnabled) }

    /** Ends the call on the SFU. */
    suspend fun hangup(reason: String = "HUNGUP") = sendCommand(CMD_HANGUP) { put("reason", reason) }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putMediaSettings(
        micEnabled: Boolean,
        cameraEnabled: Boolean,
    ) {
        putJsonObject("mediaSettings") {
            put("isAudioEnabled", micEnabled)
            put("isVideoEnabled", cameraEnabled)
            put("isScreenSharingEnabled", false)
            put("isFastScreenSharingEnabled", false)
            put("isAudioSharingEnabled", false)
            put("isAnimojiEnabled", false)
        }
    }

    private suspend fun sendCommand(
        command: String,
        params: JsonObjectBuilderScope = {},
    ) {
        val s = session ?: return
        val seq = seqLock.withLock { ++sequence }
        val msg =
            buildJsonObject {
                put("command", command)
                put("sequence", seq)
                params()
            }
        val json = msg.toString()
        clog("ws2 SEND: ${json.take(200)}")
        runCatching { s.send(json) }.onFailure { clog("ws2 send error: $it") }
    }

    fun close() {
        receiveJob?.cancel()
        scope.launch { runCatching { session?.close() } }
        session = null
    }

    private companion object {
        const val CMD_TRANSMIT_DATA = "transmit-data"
        const val CMD_ACCEPT_CALL = "accept-call"
        const val CMD_CHANGE_MEDIA_SETTINGS = "change-media-settings"
        const val CMD_HANGUP = "hangup"
    }
}

private typealias JsonObjectBuilderScope = kotlinx.serialization.json.JsonObjectBuilder.() -> Unit

/**
 * Ensures the ws2 URL carries the query params the SFU requires. The endpoint from
 * STAGE 1 has only userId/entityType/conversationId/token; the server rejects the
 * connection ("Parameter appVersion is required") without platform/appVersion/version/
 * device/capabilities/clientType/tgt — so we append any that are missing.
 */
internal fun withRequiredParams(
    endpoint: String,
    tgt: String,
): String {
    val extra =
        linkedMapOf(
            "platform" to "ANDROID",
            "appVersion" to "1.1",
            "version" to "5",
            "device" to "android",
            "capabilities" to "3c03f",
            "clientType" to "one_me",
            "deviceIdx" to "0",
            "tgt" to tgt,
        )
    val sb = StringBuilder(endpoint)
    var sep = if (endpoint.contains('?')) '&' else '?'
    for ((k, v) in extra) {
        if (!endpoint.contains("$k=")) {
            sb
                .append(sep)
                .append(k)
                .append('=')
                .append(v)
            sep = '&'
        }
    }
    return sb.toString()
}

/** Extracts a remote SDP from a `transmitted-data` notification, or null. Unit-testable. */
internal fun parseTransmittedSdp(msg: JsonObject): CallSignaling.RemoteSdp? {
    val sdp = msg["data"]?.jsonObject?.get("sdp")?.jsonObject ?: return null
    val body = sdp["sdp"]?.jsonPrimitive?.contentOrNull ?: return null
    return CallSignaling.RemoteSdp(
        type = sdp["type"]?.jsonPrimitive?.contentOrNull ?: "offer",
        sdp = body,
        participantId = msg["participantId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
    )
}

/** Extracts a remote ICE candidate from a `transmitted-data` notification, or null. Unit-testable. */
internal fun parseTransmittedCandidate(msg: JsonObject): CallSignaling.RemoteCandidate? {
    val c = msg["data"]?.jsonObject?.get("candidate")?.jsonObject ?: return null
    val cand = c["candidate"]?.jsonPrimitive?.contentOrNull ?: return null
    return CallSignaling.RemoteCandidate(
        candidate = cand,
        sdpMid = c["sdpMid"]?.jsonPrimitive?.contentOrNull,
        sdpMLineIndex = c["sdpMLineIndex"]?.jsonPrimitive?.intOrNull,
        participantId = msg["participantId"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
    )
}
