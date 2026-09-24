package com.avenarius.app.net

import com.avenarius.app.model.CallDirection
import com.avenarius.app.model.CallKind
import com.avenarius.app.model.CallMediaState
import com.avenarius.app.model.CallSetup
import com.avenarius.app.model.CallState
import com.avenarius.app.model.CallStatus
import com.avenarius.app.model.IncomingCall
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
    // Swallow stray failures: an uncaught exception in a plain scope goes to the
    // thread's default handler and, on Android, kills the process mid-call.
    private val scope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, _ -> })

    /** Parent of the per-call collectors, cancelled in [end] before the engine is torn down. */
    private var callJob: Job? = null

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

    /** Caller name/avatar supplied by the UI layer, keyed by conversationId (see [setPeerInfo]). */
    private val peerInfo = HashMap<String, Pair<String?, String?>>()

    /** Ends an unanswered inbound call after [RING_TIMEOUT_MS]. */
    private var ringTimeoutJob: Job? = null

    init {
        // The session — not the ViewModel — owns inbound calls, so a call still rings
        // (via the platform service) when the Activity and its ViewModel are gone.
        scope.launch { api.incomingCalls.collect { onIncomingCall(it) } }
    }

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
        val known = peerInfo.remove(call.conversationId)
        _state.value =
            CallState(
                conversationId = call.conversationId,
                peerId = call.callerId,
                chatId = call.chatId,
                kind = call.kind,
                direction = CallDirection.INCOMING,
                status = CallStatus.RINGING,
                peerName = peerName ?: known?.first ?: call.callerName,
                peerAvatarUrl = peerAvatarUrl ?: known?.second ?: call.callerAvatarUrl,
                media = CallMediaState(cameraEnabled = call.kind == CallKind.VIDEO),
            )
        // The caller cancelling before we answer arrives over no channel we listen to
        // yet, so an unanswered call must time out on its own instead of ringing forever.
        ringTimeoutJob?.cancel()
        ringTimeoutJob =
            scope.launch {
                delay(RING_TIMEOUT_MS)
                if (_state.value?.status == CallStatus.RINGING) end("Пропущенный звонок")
            }
    }

    /**
     * Supplies the caller's display name/avatar for [conversationId]. Applied to the
     * ringing call if it is already up, otherwise remembered for when its push lands
     * (the UI resolves names from its contact list; the push carries only an id).
     */
    fun setPeerInfo(
        conversationId: String,
        peerName: String?,
        peerAvatarUrl: String?,
    ) {
        val cur = _state.value
        if (cur != null && cur.conversationId == conversationId) {
            _state.value = cur.copy(peerName = peerName ?: cur.peerName, peerAvatarUrl = peerAvatarUrl ?: cur.peerAvatarUrl)
        } else {
            peerInfo[conversationId] = peerName to peerAvatarUrl
        }
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
        val call = pendingIncoming
        pendingIncoming = null
        if (call != null) scope.launch { runCatching { api.hangupCall(call.conversationId, "REJECTED", call.callerId) } }
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
        val job = SupervisorJob(scope.coroutineContext[Job])
        callJob = job
        // Collectors run under [job] so end() can cancel them *before* disposing the
        // PeerConnection — a late SDP/candidate hitting a disposed native peer is a SIGSEGV.
        val calls = CoroutineScope(scope.coroutineContext + job)

        calls.launch {
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
        calls.launch {
            sig.remoteSdp.collect { r ->
                if (remoteParticipantId == 0L) remoteParticipantId = r.participantId
                // setRemoteDescription/createAnswer reject (e.g. an SFU re-offer in the
                // wrong signaling state); that ends the call, it must not throw out of here.
                runCatching {
                    eng.setRemoteDescription(r.type, r.sdp)
                    if (r.type == "offer") {
                        sig.sendSdp(r.participantId, "answer", eng.createAnswer())
                    }
                }.onFailure { end("Сбой соединения") }
            }
        }
        calls.launch {
            sig.remoteCandidate.collect { c ->
                if (remoteParticipantId == 0L) remoteParticipantId = c.participantId
                runCatching { eng.addRemoteCandidate(c.candidate, c.sdpMid, c.sdpMLineIndex) }
            }
        }
        val caller = tgt == "start"
        calls.launch {
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
                    is CallSignaling.SignalingEvent.Hungup -> end(peerEndReason(e.reason))
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

    /**
     * The text shown when the *peer* ended the call, from the SFU's hangup reason. A
     * hangup that arrives before the call ever connected is the other side declining
     * (or giving up), not a normal end — say so instead of "Звонок завершён".
     */
    private fun peerEndReason(raw: String?): String {
        val s = _state.value
        val connected = s?.status == CallStatus.ACTIVE
        return when (raw?.uppercase()) {
            "FAILED" -> "Не удалось соединиться"
            "REJECTED", "DECLINED" -> "Вызов отклонён"
            "BUSY" -> "Абонент занят"
            "MISSED", "TIMEOUT", "NO_ANSWER" -> "Нет ответа"
            "CANCELED", "CANCELLED" -> "Звонок отменён"
            else ->
                when {
                    connected -> "Звонок завершён"
                    s?.direction == CallDirection.OUTGOING -> "Вызов отклонён"
                    else -> "Звонок отменён"
                }
        }
    }

    /** Ends the current call (user hangup). */
    fun hangup() {
        val s = _state.value
        val id = s?.conversationId
        // Before an answer, the caller is cancelling, not hanging up — the callee's
        // history entry (and the official client's wording) depend on which.
        val reason = if (s?.status == CallStatus.ACTIVE) "HUNGUP" else "CANCELED"
        val sig = signaling
        scope.launch {
            runCatching { sig?.hangup(reason) }
            if (!id.isNullOrEmpty()) runCatching { api.hangupCall(id, reason, s?.peerId) }
        }
        end(null)
    }

    private fun end(reason: String?) {
        // Idempotent: the first end() wins. A normal hangup (peer Hungup / user) is
        // immediately followed by ICE->CLOSED emitting Failed; without this guard that
        // would clobber the real reason with a spurious "Сбой соединения".
        if (_state.value?.status == CallStatus.ENDED) return
        _state.update { it?.copy(status = CallStatus.ENDED, endReason = reason) }
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        callJob?.cancel()
        callJob = null
        val sig = signaling
        val eng = engine
        signaling = null
        engine = null
        // PeerConnection.dispose() blocks until the native signaling/network threads
        // drain (up to seconds) — hangup is tapped on the main thread, so do it off it.
        scope.launch(Dispatchers.Default) {
            runCatching { sig?.close() }
            runCatching { eng?.close() }
        }
        setup = null
        pendingIncoming = null
        remoteParticipantId = 0L
        offerSent = false
    }

    /** Clears an ENDED call from state (after the UI has shown the end reason). */
    fun clear() {
        _state.value = null
    }

    private companion object {
        const val RING_TIMEOUT_MS = 60_000L
    }
}
