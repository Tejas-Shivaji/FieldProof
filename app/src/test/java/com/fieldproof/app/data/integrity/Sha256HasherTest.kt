package com.fieldproof.app.data.integrity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class Sha256HasherTest {
    @Test
    fun emptyInput_matchesKnownDigest() {
        val result = Sha256Hasher.hash(ByteArrayInputStream(ByteArray(0)))
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            result.hex,
        )
        assertEquals(0L, result.byteLength)
    }

    @Test
    fun abc_matchesKnownDigest() {
        val result = Sha256Hasher.hash(ByteArrayInputStream("abc".toByteArray()))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            result.hex,
        )
        assertEquals(3L, result.byteLength)
    }

    @Test
    fun oneFlippedBit_changesDigest() {
        val a = Sha256Hasher.hash(ByteArrayInputStream("bridge damaged".toByteArray()))
        val b = Sha256Hasher.hash(ByteArrayInputStream("bridge Damaged".toByteArray()))
        assertFalse(Sha256Hasher.equalHex(a.hex, b.hex))
    }

    @Test
    fun equalHex_isCaseInsensitiveAndConstantTimeApi() {
        val hex = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertTrue(Sha256Hasher.equalHex(hex, hex.uppercase()))
    }
}
