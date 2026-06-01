package com.avenarius.app.net

/**
 * LZ4 *block* format decompressor (not the frame format).
 *
 * The Max mobile server compresses larger response payloads with LZ4; the frame
 * header's top length byte flags it. We only ever need to *decompress* responses
 * (requests are sent uncompressed), so only the decoder is implemented.
 *
 * Block format: a sequence of [token | literals | offset | match] groups.
 *   token high nibble = literal length, low nibble = match length - 4.
 *   Lengths of 15 are extended by extra 0xFF... bytes.
 *   offset is a little-endian u16 back-reference into already-decoded output.
 * Spec: https://github.com/lz4/lz4/blob/dev/doc/lz4_Block_format.md
 */
object Lz4 {
    fun decompressBlock(src: ByteArray): ByteArray {
        var out = ByteArray(src.size * 3 + 64)
        var outLen = 0
        var i = 0

        fun ensure(extra: Int) {
            if (outLen + extra > out.size) {
                var n = out.size * 2
                while (n < outLen + extra) n *= 2
                out = out.copyOf(n)
            }
        }

        while (i < src.size) {
            val token = src[i++].toInt() and 0xff

            // Literals
            var literalLen = token ushr 4
            if (literalLen == 15) {
                var b: Int
                do {
                    b = src[i++].toInt() and 0xff
                    literalLen += b
                } while (b == 255)
            }
            ensure(literalLen)
            src.copyInto(out, outLen, i, i + literalLen)
            outLen += literalLen
            i += literalLen

            // The last sequence is literals only; the block ends here.
            if (i >= src.size) break

            // Match
            val offset = (src[i++].toInt() and 0xff) or ((src[i++].toInt() and 0xff) shl 8)
            var matchLen = (token and 0x0f) + 4
            if ((token and 0x0f) == 15) {
                var b: Int
                do {
                    b = src[i++].toInt() and 0xff
                    matchLen += b
                } while (b == 255)
            }
            ensure(matchLen)
            var matchPos = outLen - offset
            // Byte-by-byte to support overlapping copies (offset < matchLen).
            repeat(matchLen) {
                out[outLen++] = out[matchPos++]
            }
        }
        return out.copyOf(outLen)
    }
}
