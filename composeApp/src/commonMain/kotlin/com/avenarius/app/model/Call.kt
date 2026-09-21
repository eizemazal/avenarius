package com.avenarius.app.model

import kotlinx.serialization.Serializable

/*
 * Domain models for voice/video calls.
 *
 * A Max call has two signaling stages (reverse-engineered from the official
 * client — see the memory note "max-calls-feasibility"):
 *
 *  STAGE 1 — call setup over the Max socket (VIDEO_CHAT_START_ACTIVE, opcode 78).
 *    We send {conversationId, calleeIds, internalParams, isVideo}; the server replies
 *    with `internalCallerParams`, a JSON blob describing the media SFU: a WebSocket
 *    (ws2) + WebTransport endpoint, and per-call ICE (TURN/STUN) credentials.
 *    That blob is parsed into [CallSetup].
 *
 *  STAGE 2 — WebRTC signaling to the SFU (videowebrtc.okcdn.ru). We connect to the
 *    ws2 endpoint and exchange SDP offer/answer + ICE candidates as JSON commands;
 *    media is standard WebRTC (Opus / H264+VP8+VP9 / DTLS-SRTP over ICE). See
 *    [CallSignaling] and the platform CallEngine.
 *
 * These models live in commonMain and are shared by Android (real media) and
 * desktop (no-op) — the desktop build carries the types but never places a call.
 */

/** Whether a call carries video, or is audio-only. */
enum class CallKind { AUDIO, VIDEO }

/** Which side started the call. */
enum class CallDirection { OUTGOING, INCOMING }

/**
 * The lifecycle of an active call, driving the in-call UI.
 *
 *  - [DIALING]     — outgoing, waiting for the peer to accept (STAGE 1 sent).
 *  - [RINGING]     — incoming, waiting for us to accept/decline.
 *  - [CONNECTING]  — accepted; establishing the SFU/WebRTC session (STAGE 2).
 *  - [ACTIVE]      — media is flowing.
 *  - [ENDED]       — hung up, declined, failed, or the peer left.
 */
enum class CallStatus { DIALING, RINGING, CONNECTING, ACTIVE, ENDED }

/** A single ICE server (STUN or TURN) with optional short-term credentials. */
@Serializable
data class IceServer(
    val urls: List<String>,
    val username: String? = null,
    val credential: String? = null,
)

/**
 * STAGE-1 result: everything needed to open the media session. Parsed from the
 * `internalCallerParams` JSON string in the VIDEO_CHAT_START_ACTIVE reply.
 */
@Serializable
data class CallSetup(
    val conversationId: String,
    /** Our participant id inside the conversation (SFU peer id). */
    val internalId: Long,
    /** Our Max user id, as the SFU sees it. */
    val externalId: String,
    /** ws2 signaling endpoint: wss://videowebrtc.okcdn.ru/ws2?...&token=... */
    val wsEndpoint: String,
    /** WebTransport endpoint (unused on Android — we prefer ws2). */
    val wtEndpoint: String? = null,
    val iceServers: List<IceServer> = emptyList(),
)

/**
 * An inbound call the user can accept or decline (from a NOTIF_CALL_START push).
 * The setup (SFU endpoints) is fetched when the user accepts.
 */
@Serializable
data class IncomingCall(
    val conversationId: String,
    val callerId: Long,
    val chatId: Long,
    val kind: CallKind,
    val callerName: String? = null,
    val callerAvatarUrl: String? = null,
    /**
     * The callee's SFU params, parsed from the push's `vcp` blob. The callee joins the
     * SFU with this directly — it must NOT call VIDEO_CHAT_START_ACTIVE (the server
     * rejects that for an inbound call with error 1114).
     */
    val setup: CallSetup? = null,
)

/** Local media toggles during a call. */
@Serializable
data class CallMediaState(
    val micEnabled: Boolean = true,
    val cameraEnabled: Boolean = true,
    val speakerOn: Boolean = false,
    /** True once the remote peer's video track is being received. */
    val remoteVideoActive: Boolean = false,
)

/** The full state of the one in-progress call (null when there is no call). */
@Serializable
data class CallState(
    val conversationId: String,
    val peerId: Long,
    val chatId: Long,
    val kind: CallKind,
    val direction: CallDirection,
    val status: CallStatus,
    val peerName: String? = null,
    val peerAvatarUrl: String? = null,
    val media: CallMediaState = CallMediaState(),
    /** Wall-clock ms when the call became [CallStatus.ACTIVE], for the duration timer. */
    val connectedAtMs: Long? = null,
    /** A short reason shown when the call ends (e.g. "No network", "Declined"). */
    val endReason: String? = null,
)
