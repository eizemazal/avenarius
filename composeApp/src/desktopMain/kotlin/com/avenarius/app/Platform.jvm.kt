package com.avenarius.app.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets

actual fun createHttpClient(): HttpClient =
    HttpClient(CIO) {
        install(WebSockets)
    }
