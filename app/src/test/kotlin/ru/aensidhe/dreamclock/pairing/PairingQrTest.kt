package ru.aensidhe.dreamclock.pairing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PairingQrTest {
    @Test
    fun encodes_a_non_empty_square_matrix() {
        val matrix = PairingQr.matrix("http://192.168.1.42:8973/#k=abc", size = 256)
        assertEquals(matrix.width, matrix.height)
        var dark = 0
        for (x in 0 until matrix.width) {
            if (matrix.get(x, matrix.height / 2)) dark++
        }
        assertTrue(dark > 0)
    }
}
