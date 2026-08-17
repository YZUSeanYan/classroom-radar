package com.classroomradar.app.audio

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

/** Writes a mono 16 kHz PCM WAV entirely in the app-private files directory. */
class WavFileWriter(private val file: File) : AutoCloseable {
    private val output = FileOutputStream(file)
    private var pcmBytes = 0L
    private var closed = false

    init {
        output.write(ByteArray(HEADER_SIZE))
    }

    fun write(samples: ShortArray, count: Int) {
        check(!closed) { "WAV writer is closed." }
        if (count <= 0) return
        val bytes = ByteArray(count * 2)
        for (index in 0 until count) {
            val value = samples[index].toInt()
            bytes[index * 2] = value.toByte()
            bytes[index * 2 + 1] = (value ushr 8).toByte()
        }
        output.write(bytes)
        pcmBytes += bytes.size
    }

    override fun close() {
        if (closed) return
        closed = true
        output.close()
        RandomAccessFile(file, "rw").use { wav ->
            wav.writeAscii("RIFF")
            wav.writeIntLe((36 + pcmBytes).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            wav.writeAscii("WAVEfmt ")
            wav.writeIntLe(16)
            wav.writeShortLe(1)
            wav.writeShortLe(CHANNELS)
            wav.writeIntLe(SAMPLE_RATE)
            wav.writeIntLe(SAMPLE_RATE * CHANNELS * BYTES_PER_SAMPLE)
            wav.writeShortLe(CHANNELS * BYTES_PER_SAMPLE)
            wav.writeShortLe(BITS_PER_SAMPLE)
            wav.writeAscii("data")
            wav.writeIntLe(pcmBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }
    }

    private fun RandomAccessFile.writeAscii(value: String) = write(value.encodeToByteArray())
    private fun RandomAccessFile.writeIntLe(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
        write(value ushr 16 and 0xFF)
        write(value ushr 24 and 0xFF)
    }
    private fun RandomAccessFile.writeShortLe(value: Int) {
        write(value and 0xFF)
        write(value ushr 8 and 0xFF)
    }

    private companion object {
        const val HEADER_SIZE = 44
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BYTES_PER_SAMPLE = 2
        const val BITS_PER_SAMPLE = 16
    }
}
