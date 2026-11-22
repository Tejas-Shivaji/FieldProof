package com.fieldproof.app.data.integrity

import java.io.InputStream
import java.security.MessageDigest

data class HashResult(
    val digest: ByteArray,
    val hex: String,
    val byteLength: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HashResult) return false
        return hex == other.hex && byteLength == other.byteLength
    }

    override fun hashCode(): Int = hex.hashCode() * 31 + byteLength.hashCode()
}

/**
 * Streams file bytes through SHA-256. Does not load the whole file into memory.
 *
 * A matching digest means: these bytes equal the bytes hashed when the digest
 * was recorded. It is not a proof of authenticity, authorship, GPS, or time.
 */
object Sha256Hasher {
    fun hash(input: InputStream): HashResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            total += read
        }
        val bytes = digest.digest()
        return HashResult(digest = bytes, hex = toHex(bytes), byteLength = total)
    }

    fun equalHex(left: String, right: String): Boolean {
        val a = fromHex(left.trim().lowercase())
        val b = fromHex(right.trim().lowercase())
        return MessageDigest.isEqual(a, b)
    }

    fun toHex(bytes: ByteArray): String {
        val out = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            out.append(HEX[(b.toInt() ushr 4) and 0xF])
            out.append(HEX[b.toInt() and 0xF])
        }
        return out.toString()
    }

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim().lowercase()
        require(clean.length % 2 == 0) { "hex length must be even" }
        val out = ByteArray(clean.length / 2)
        var i = 0
        while (i < clean.length) {
            val hi = Character.digit(clean[i], 16)
            val lo = Character.digit(clean[i + 1], 16)
            require(hi >= 0 && lo >= 0) { "invalid hex" }
            out[i / 2] = ((hi shl 4) + lo).toByte()
            i += 2
        }
        return out
    }

    private val HEX = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
    )
}
