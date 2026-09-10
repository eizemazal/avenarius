package com.avenarius.app.net

/**
 * The `mode` attestation the Max server requires before it will dispatch an SMS login
 * code. Without it the AUTH_REQUEST is accepted (a token comes back) but no SMS is sent,
 * which is how Max locks out third-party clients.
 *
 * The genuine client computes, per session:
 *
 *   mode = SHA256(H_cert ‖ callsSeed ‖ deviceId)
 *        ‖ SHA256(H_dex  ‖ callsSeed ‖ deviceId)
 *        ‖ SHA256(H_libs ‖ callsSeed ‖ deviceId)
 *
 * where `callsSeed` is the per-session value from the SESSION_INIT reply (8 bytes,
 * big-endian), `deviceId` is the id we already send in the handshake (any value — the
 * server validates the token against the deviceId WE sent, so our own works), and the
 * three H_* are SHA-256 hashes of the genuine app's signing certificate, dex files and
 * native libraries. Those are CONSTANTS for a given Max version+arch — there is no secret
 * key; it is a self-integrity attestation. See [AUTH_ATTEST_CONSTANTS] for how to refresh
 * them when Max force-bumps the required version.
 */
internal object AuthAttest {
    // Genuine Max app 26.31.0 / arm64-v8a. Refresh together with APP_VERSION/BUILD_NUMBER
    // whenever Max forces an update (H_cert is stable — same signing key; H_dex and H_libs
    // change per build). Recompute from the new MAX.apk: SHA-256 of the signing cert, of
    // the dex files, and of the arm64 native libs (see AuthAttestTest for the reference
    // vector to re-pin).
    private val H_CERT = "1684414033eb263e2c615f8b7df5ed8793850a07656304997fbf07e9e21e1e93".hexBytes()
    private val H_DEX = "9affa687874d88ea80b298949f826bcf8c1bba36f24d48c44b8f54cdbf01c96f".hexBytes()
    private val H_LIBS = "38e2e5de3d4a9010ea1053eb28573593683c5b2bb16b09914a8822476c8aab8c".hexBytes()

    /** Computes the 96-byte `mode` token for this session. */
    fun mode(
        callsSeed: Long,
        deviceId: String,
    ): ByteArray {
        val seed = ByteArray(8) { ((callsSeed ushr ((7 - it) * 8)) and 0xff).toByte() }
        val dev = deviceId.encodeToByteArray()

        fun block(h: ByteArray) = sha256(h + seed + dev)
        return block(H_CERT) + block(H_DEX) + block(H_LIBS)
    }

    /**
     * MessagePack for the AUTH_REQUEST body `{mode, type, phone}`, including the genuine
     * client's 2-byte `f0 7c` payload prefix. Hand-built because [MsgPack] speaks JSON and
     * `mode` is a binary (`bin8`) field.
     */
    fun startAuthPayload(
        mode: ByteArray,
        phone: String,
    ): ByteArray {
        val out = ArrayList<Byte>(mode.size + phone.length + 32)
        out.add(0xf0.toByte())
        out.add(0x7c) // constant genuine-client prefix
        out.add(0x83.toByte()) // fixmap, 3 entries

        fun str(s: String) {
            val b = s.encodeToByteArray()
            require(b.size < 32) { "string too long for fixstr: $s" }
            out.add((0xa0 or b.size).toByte())
            b.forEach { out.add(it) }
        }
        str("mode")
        out.add(0xc4.toByte()) // bin8
        out.add(mode.size.toByte())
        mode.forEach { out.add(it) }
        str("type")
        str("START_AUTH")
        str("phone")
        str(phone)
        return out.toByteArray()
    }
}

private fun String.hexBytes(): ByteArray =
    ByteArray(length / 2) {
        ((this[it * 2].digitToInt(16) shl 4) or this[it * 2 + 1].digitToInt(16)).toByte()
    }
