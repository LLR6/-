package com.lr.immersiveaudiobook.data.importer

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object EncodingDetector {
    private const val SAMPLE_SIZE = 64 * 1024

    fun detect(file: File): Charset {
        val sample = file.inputStream().buffered().use { input ->
            val bytes = ByteArray(SAMPLE_SIZE)
            val count = input.read(bytes)
            if (count <= 0) ByteArray(0) else bytes.copyOf(count)
        }
        if (sample.size >= 3 &&
            sample[0] == 0xEF.toByte() &&
            sample[1] == 0xBB.toByte() &&
            sample[2] == 0xBF.toByte()
        ) {
            return StandardCharsets.UTF_8
        }
        if (sample.size >= 2) {
            if (sample[0] == 0xFF.toByte() && sample[1] == 0xFE.toByte()) {
                return StandardCharsets.UTF_16LE
            }
            if (sample[0] == 0xFE.toByte() && sample[1] == 0xFF.toByte()) {
                return StandardCharsets.UTF_16BE
            }
        }
        if (isValidUtf8(sample)) return StandardCharsets.UTF_8
        return Charset.forName("GB18030")
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
        true
    } catch (_: CharacterCodingException) {
        false
    }
}
