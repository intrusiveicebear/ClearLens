package com.clearlens.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageAnalyzerTest {
    @Test
    fun hammingDistance_countsChangedBits() {
        assertEquals(0, ImageAnalyzer.hammingDistance(0b1010, 0b1010))
        assertEquals(2, ImageAnalyzer.hammingDistance(0b1010, 0b0011))
        assertEquals(64, ImageAnalyzer.hammingDistance(0L, -1L))
    }

    @Test
    fun sha256_matchesKnownValue() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ImageAnalyzer.sha256("abc".encodeToByteArray())
        )
    }
}
