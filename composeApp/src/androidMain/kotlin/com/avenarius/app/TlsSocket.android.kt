package com.avenarius.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

actual class TlsSocket actual constructor(
    private val host: String,
    private val port: Int,
) {
    private var socket: SSLSocket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null

    actual suspend fun connect() =
        withContext(Dispatchers.IO) {
            val s = SSLSocketFactory.getDefault().createSocket() as SSLSocket
            try {
                // Bounded connect + handshake: without a timeout an unreachable host keeps
                // the login spinner going for the OS TCP timeout (~2 min).
                s.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
                s.soTimeout = HANDSHAKE_TIMEOUT_MS
                s.startHandshake()
                // No read timeout afterwards: liveness is judged by the transport's ping
                // loop (which also counts pushes), not by a blind idle timer.
                s.soTimeout = 0
            } catch (t: Throwable) {
                runCatching { s.close() }
                throw t
            }
            socket = s
            input = DataInputStream(s.inputStream)
            output = s.outputStream
        }

    actual suspend fun write(bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            val o = output ?: error("socket not connected")
            o.write(bytes)
            o.flush()
        }

    actual suspend fun readFully(buffer: ByteArray) =
        withContext(Dispatchers.IO) {
            (input ?: error("socket not connected")).readFully(buffer)
        }

    actual fun close() {
        runCatching { socket?.close() }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val HANDSHAKE_TIMEOUT_MS = 20_000
    }
}
