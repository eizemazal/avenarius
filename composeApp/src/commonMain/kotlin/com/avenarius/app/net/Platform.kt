package com.avenarius.app.net

import io.ktor.client.HttpClient

/**
 * The only part of the networking layer that differs per platform: which Ktor
 * engine backs the WebSocket. Android uses OkHttp, desktop uses CIO.
 * Everything else in [MaxClient] is shared.
 */
expect fun createHttpClient(): HttpClient
