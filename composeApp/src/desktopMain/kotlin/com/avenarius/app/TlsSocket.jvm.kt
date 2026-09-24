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
                // Bounded connect + handshake; no read timeout afterwards — liveness is
                // judged by the transport's ping loop, not by a blind idle timer.
                s.connect(InetSocketAddress(host, port), 15_000)
                s.soTimeout = 20_000
                s.startHandshake()
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
}
