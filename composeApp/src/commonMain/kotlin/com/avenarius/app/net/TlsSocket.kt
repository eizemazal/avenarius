package com.avenarius.app.net

/**
 * A blocking TLS client socket, exposed as suspend functions. The mobile Max
 * protocol runs over a raw TLS/TCP connection (not HTTP), so we need plain
 * socket I/O. Implemented per platform; both Android and desktop use the JVM's
 * built-in SSLSocket, so the actuals are tiny.
 */
expect class TlsSocket(
    host: String,
    port: Int,
) {
    suspend fun connect()

    suspend fun write(bytes: ByteArray)

    /** Reads exactly [buffer].size bytes, suspending until they arrive. */
    suspend fun readFully(buffer: ByteArray)

    fun close()
}
