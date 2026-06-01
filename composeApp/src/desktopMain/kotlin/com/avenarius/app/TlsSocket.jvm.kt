package com.avenarius.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.OutputStream
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
            val s = SSLSocketFactory.getDefault().createSocket(host, port) as SSLSocket
            s.startHandshake()
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
