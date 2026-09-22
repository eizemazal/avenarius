package com.avenarius.app.net

import com.avenarius.app.model.CallDirection
import com.avenarius.app.model.CallKind
import com.avenarius.app.model.CallMediaState
import com.avenarius.app.model.CallSetup
import com.avenarius.app.model.CallState
import com.avenarius.app.model.CallStatus
import com.avenarius.app.model.IncomingCall
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Orchestrates one call end-to-end, tying together the three layers:
 *   STAGE 1 — [MaxApi.startCall]/[MaxApi.acceptCall] (SFU endpoints + ICE creds),
 *   STAGE 2 — [CallSignaling] (SDP/ICE exchange over the ws2 WebSocket), and
 *   media    — [CallEngine] (WebRTC PeerConnection).
 *
 * The protocol is **answer-driven** (as the web client is, in 1:1 "single" mode): the
 * SFU sends the offer, we answer. So the session waits for a remote offer, applies it,
 * creates an answer, and trickles ICE both ways. [state] drives the call UI.
 *
 * Lives in commonMain; the platform only supplies the [CallEngine] via [createCallEngine]
 * (real on Android, no-op on desktop) and, for the video renderer, reads [engine].
 */
class CallSession(
    private val api: MaxApi,
    private val nowMs: () -> Long,
    private val httpFactory: () -> HttpClient = ::createHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow<CallState?>(null)
    val state: StateFlow<CallState?> = _state.asStateFlow()

    /** The active media engine, exposed so the platform video renderer can bind to it. */
    var engine: CallEngine? = null
        private set

    private var signaling: CallSignaling? = null
    private var setup: CallSetup? = null

    /** SFU participant id of the remote peer, learned from the first signaling message. */
    private var remoteParticipantId: Long = 0L

    /** Caller only: guard so we send our offer exactly once. */
    private var offerSent = false

    /**
     * Caller side: once we know the remote participant, create and send our SDP offer.
     * (The callee is answer-driven and never calls this — the caller offers, callee answers.)
     */
    private suspend fun maybeSendOffer(
        eng: CallEngine,
        sig: CallSignaling,
    ) {
        if (offerSent || remoteParticipantId == 0L) return
        offerSent = true
        runCatching { sig.sendSdp(remoteParticipantId, "offer", eng.createOffer()) }
    }

    /** Set for an inbound call awaiting accept/decline (STAGE-1 setup not fetched yet). */
    private var pendingIncoming: IncomingCall? = null

    // ------------------------------------------------------------------ outgoing

    /** Places an outgoing call. */
    fun placeCall(
        peerId: Long,
        chatId: Long,
        isVideo: Boolean,
        peerName: String? = null,
        peerAvatarUrl: String? = null,
    ) {
        if (_state.value != null) return
        val kind = if (isVideo) CallKind.VIDEO else CallKind.AUDIO
        _state.value =
            CallState(
                conversationId = "",
                peerId = peerId,
                chatId = chatId,
                kind = kind,
                direction = CallDirection.OUTGOING,
                status = CallStatus.DIALING,
                peerName = peerName,
                peerAvatarUrl = peerAvatarUrl,
                media = CallMediaState(cameraEnabled = isVideo),
            )
        scope.launch {
            runCatching {
                val s = api.startCall(peerId, isVideo)
                _state.update { it?.copy(conversationId = s.conversationId) }
                beginMedia(s, isVideo, tgt = "start")
            }.onFailure { end("Не удалось начать звонок") }
        }
    }

    // ------------------------------------------------------------------ incoming

    /** An inbound call is ringing (from [MaxApi.incomingCalls]); show the accept UI. */
    fun onIncomingCall(
        call: IncomingCall,
        peerName: String? = null,
        peerAvatarUrl: String? = null,
    ) {
        if (_state.value != null) return
        pendingIncoming = call
        _state.value =
            CallState(
                conversationId = call.conversationId,
                peerId = call.callerId,
                chatId = call.chatId,
                kind = call.kind,
                direction = CallDirection.INCOMING,
                status = CallStatus.RINGING,
                peerName = peerName ?: call.callerName,
                peerAvatarUrl = peerAvatarUrl ?: call.callerAvatarUrl,
                media = CallMediaState(cameraEnabled = call.kind == CallKind.VIDEO),
            )
    }

    /** Accepts the ringing inbound call. */
    fun accept() {
        val call = pendingIncoming ?: return
        val isVideo = call.kind == CallKind.VIDEO
        _state.update { it?.copy(status = CallStatus.CONNECTING) }
        scope.launch {
            runCatching {
                // The callee's SFU params come from the push's `vcp` (in call.setup); it must
                // NOT call VIDEO_CHAT_START_ACTIVE (the server rejects that with error 1114).
                val s = call.setup ?: api.acceptCall(call.conversationId, call.callerId, isVideo)
                beginMedia(s, isVideo, tgt = "accept")
                signaling?.acceptCall(micEnabled = true, cameraEnabled = isVideo)
            }.onFailure { end("Не удалось подключиться") }
        }
    }

    /** Declines the ringing inbound call. */
    fun decline() {
        val id = pendingIncoming?.conversationId
        pendingIncoming = null
        if (id != null) scope.launch { runCatching { api.hangupCall(id) } }
        end("Отклонён")
    }

    // ------------------------------------------------------------------ media

    private suspend fun beginMedia(
        s: CallSetup,
        isVideo: Boolean,
        tgt: String,
    ) {
        setup = s
        _state.update { it?.copy(conversationId = s.conversationId, status = CallStatus.CONNECTING) }
        val eng = createCallEngine(s.iceServers, isVideo)
        engine = eng
        val sig = CallSignaling(s, tgt, httpFactory())
        signaling = sig

        scope.launch {
            eng.events.collect { ev ->
                when (ev) {
                    is CallEngineEvent.LocalCandidate ->
                        sig.sendCandidate(remoteParticipantId, ev.candidate, ev.sdpMid, ev.sdpMLineIndex)
                    CallEngineEvent.Connected ->
                        _state.update {
                            it?.copy(status = CallStatus.ACTIVE, connectedAtMs = it.connectedAtMs ?: nowMs())
                        }
                    is CallEngineEvent.RemoteVideo ->
                        _state.update { it?.copy(media = it.media.copy(remoteVideoActive = ev.active)) }
                    CallEngineEvent.Failed -> end("Сбой соединения")
                }
            }
        }
        scope.launch {
            sig.remoteSdp.collect { r ->
                if (remoteParticipantId == 0L) remoteParticipantId = r.participantId
                eng.setRemoteDescription(r.type, r.sdp)
                if (r.type == "offer") {
                    sig.sendSdp(r.participantId, "answer", eng.createAnswer())
                }
            }
        }
        scope.launch {
            sig.remoteCandidate.collect { c ->
                if (remoteParticipantId == 0L) remoteParticipantId = c.participantId
                eng.addRemoteCandidate(c.candidate, c.sdpMid, c.sdpMLineIndex)
            }
        }
        val caller = tgt == "start"
        scope.launch {
            sig.events.collect { e ->
                when (e) {
                    is CallSignaling.SignalingEvent.PeerRegistered -> {
                        if (remoteParticipantId == 0L) remoteParticipantId = e.participantId
                        if (caller) maybeSendOffer(eng, sig)
                    }
                    is CallSignaling.SignalingEvent.Accepted -> {
                        if (remoteParticipantId == 0L) remoteParticipantId = e.participantId
                        if (caller) maybeSendOffer(eng, sig)
                    }
                    is CallSignaling.SignalingEvent.Hungup -> {
                        // The peer ended it. Map the raw technical reason (HUNGUP/CANCELED/…)
                        // to a friendly message; only surface a distinct text for a failure.
                        val reason = if (e.reason == "FAILED") "Не удалось соединиться" else "Звонок завершён"
                        end(reason)
                    }
                    else -> Unit
                }
            }
        }
        sig.connect()
        // The caller has no accept-call to declare its media; announce it so the SFU
        // sets up our producer and relays the peer's offer.
        if (caller) {
            runCatching { sig.changeMediaSettings(micEnabled = true, cameraEnabled = isVideo) }
        }
    }

    // ------------------------------------------------------------------ controls

    fun toggleMic() {
        val on = !(_state.value?.media?.micEnabled ?: true)
        engine?.setMicEnabled(on)
        _state.update { it?.copy(media = it.media.copy(micEnabled = on)) }
        notifyMediaSettings()
    }

    fun toggleCamera() {
        val on = !(_state.value?.media?.cameraEnabled ?: true)
        engine?.setCameraEnabled(on)
        _state.update { it?.copy(media = it.media.copy(cameraEnabled = on)) }
        notifyMediaSettings()
    }

    /** Tells the peer our current mic/camera state, so their UI hides/shows our video. */
    private fun notifyMediaSettings() {
        val m = _state.value?.media ?: return
        val sig = signaling ?: return
        scope.launch { runCatching { sig.changeMediaSettings(m.micEnabled, m.cameraEnabled) } }
    }

    fun switchCamera() = engine?.switchCamera() ?: Unit

    fun setSpeaker(on: Boolean) = _state.update { it?.copy(media = it.media.copy(speakerOn = on)) }

    /** Ends the current call (user hangup). */
    fun hangup() {
        val id = _state.value?.conversationId
        scope.launch {
            runCatching { signaling?.hangup() }
            if (!id.isNullOrEmpty()) runCatching { api.hangupCall(id) }
        }
        end(null)
    }

    private fun end(reason: String?) {
        // Idempotent: the first end() wins. A normal hangup (peer Hungup / user) is
        // immediately followed by ICE->CLOSED emitting Failed; without this guard that
        // would clobber the real reason with a spurious "Сбой соединения".
        if (_state.value?.status == CallStatus.ENDED) return
        _state.update { it?.copy(status = CallStatus.ENDED, endReason = reason) }
        runCatching { signaling?.close() }
        runCatching { engine?.close() }
        signaling = null
        engine = null
        setup = null
        pendingIncoming = null
        remoteParticipantId = 0L
        offerSent = false
    }

    /** Clears an ENDED call from state (after the UI has shown the end reason). */
    fun clear() {
        _state.value = null
    }
}
