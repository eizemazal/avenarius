package com.avenarius.app.net

import com.avenarius.app.model.CallSetup
import com.avenarius.app.model.IceServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Parses the `internalCallerParams` JSON string from a VIDEO_CHAT_START_ACTIVE reply
 * into a [CallSetup]. Extracted from [MaxClient] so it can be unit-tested against a
 * real captured reply (see CallSetupParserTest). [conversationId] is the fallback used
 * when the blob omits it.
 */
internal fun parseInternalCallerParams(
    conversationId: String,
    raw: String,
): CallSetup {
    val p = Json.parseToJsonElement(raw).jsonObject
    val idObj = p["id"]?.jsonObject
    val iceServers =
        buildList {
            p["turn"]?.jsonObject?.let { t ->
                add(
                    IceServer(
                        urls = t.stringList("urls"),
                        username = t["username"]?.jsonPrimitive?.contentOrNull,
                        credential = t["credential"]?.jsonPrimitive?.contentOrNull,
                    ),
                )
            }
            p["stun"]?.jsonObject?.let { s -> add(IceServer(urls = s.stringList("urls"))) }
        }
    return CallSetup(
        conversationId = p["conversationId"]?.jsonPrimitive?.contentOrNull ?: conversationId,
        internalId =
            idObj
                ?.get("internal")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.toLongOrNull() ?: 0L,
        externalId = idObj?.get("external")?.jsonPrimitive?.contentOrNull ?: "",
        wsEndpoint = p["endpoint"]?.jsonPrimitive?.contentOrNull ?: "",
        wtEndpoint = p["wtEndpoint"]?.jsonPrimitive?.contentOrNull,
        iceServers = iceServers,
    )
}

private fun JsonObject.stringList(key: String): List<String> =
    this[key]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull } ?: emptyList()

/**
 * Parses the `vcp` blob from a NOTIF_CALL_START push into a [CallSetup] for the callee.
 * Format: `<uncompressedLen>:<base64( LZ4-block( JSON ) )>`. The JSON uses short keys:
 *   tkn=token, wse=ws endpoint (bare), stne=STUN url, trne=comma-separated TURN urls,
 *   trnu=TURN username ("<epoch>:<ourInternalId>"), trnp=TURN credential.
 * The callee joins the SFU with this — it must not call VIDEO_CHAT_START_ACTIVE.
 */
@OptIn(ExperimentalEncodingApi::class)
internal fun parseVcp(
    conversationId: String,
    vcp: String,
): CallSetup {
    val b64 = vcp.substringAfter(':', vcp)
    val padded = b64 + "=".repeat((4 - b64.length % 4) % 4)
    val json = Lz4.decompressBlock(Base64.Default.decode(padded)).decodeToString()
    val p = Json.parseToJsonElement(json).jsonObject
    val tkn = p["tkn"]?.jsonPrimitive?.contentOrNull ?: ""
    val wse = p["wse"]?.jsonPrimitive?.contentOrNull ?: ""
    val trnu = p["trnu"]?.jsonPrimitive?.contentOrNull
    val trnp = p["trnp"]?.jsonPrimitive?.contentOrNull
    val internalId = trnu?.substringAfterLast(':')?.toLongOrNull() ?: 0L
    val iceServers =
        buildList {
            p["trne"]?.jsonPrimitive?.contentOrNull?.let { urls ->
                add(IceServer(urls = urls.split(',').map { it.trim() }.filter { it.isNotEmpty() }, username = trnu, credential = trnp))
            }
            p["stne"]?.jsonPrimitive?.contentOrNull?.let { add(IceServer(urls = listOf(it))) }
        }
    val wsEndpoint = "$wse?userId=$internalId&entityType=USER&conversationId=$conversationId&token=$tkn"
    return CallSetup(
        conversationId = conversationId,
        internalId = internalId,
        externalId = "",
        wsEndpoint = wsEndpoint,
        wtEndpoint = p["wte"]?.jsonPrimitive?.contentOrNull,
        iceServers = iceServers,
    )
}
