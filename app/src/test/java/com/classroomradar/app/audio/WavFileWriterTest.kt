package com.classroomradar.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WavFileWriterTest {
    @Test
    fun `writes a valid mono pcm wav header`() {
        val file = File.createTempFile("water-class-radar", ".wav")
        try {
            WavFileWriter(file).use { it.write(shortArrayOf(0, 1, -1), 3) }
            val bytes = file.readBytes()

            assertEquals(50, bytes.size)
            assertEquals("RIFF", bytes.copyOfRange(0, 4).decodeToString())
            assertEquals("WAVE", bytes.copyOfRange(8, 12).decodeToString())
            assertEquals("data", bytes.copyOfRange(36, 40).decodeToString())
            assertEquals(6, littleEndianInt(bytes, 40))
            assertTrue(bytes.copyOfRange(44, 50).contentEquals(byteArrayOf(0, 0, 1, 0, -1, -1)))
        } finally {
            file.delete()
        }
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
