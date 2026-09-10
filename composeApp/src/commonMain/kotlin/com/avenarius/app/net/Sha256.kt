package com.avenarius.app.net

/** SHA-256 of [data]. Both app targets are JVM, so the actual lives in `jvmShared`. */
internal expect fun sha256(data: ByteArray): ByteArray
