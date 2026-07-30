package com.lr.immersiveaudiobook.data.importer

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import kotlin.math.max

object EncodingDetector {
    private const val SAMPLE_SIZE = 256 * 1024
    const val AUTO = "AUTO"

    private val supportedCharsets = listOf(
        StandardCharsets.UTF_8,
        Charset.forName("GB18030"),
        Charset.forName("Big5"),
        StandardCharsets.UTF_16LE,
        StandardCharsets.UTF_16BE
    )

    fun detect(file: File, preferredEncoding: String = AUTO): Charset {
        charsetForName(preferredEncoding)?.let { return it }
        val sample = file.inputStream().buffered().use { input ->
            val bytes = ByteArray(SAMPLE_SIZE)
            val count = input.read(bytes)
            if (count <= 0) ByteArray(0) else bytes.copyOf(count)
        }
        if (sample.isEmpty()) return StandardCharsets.UTF_8
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

        return supportedCharsets
            .mapNotNull { charset -> decodeStrict(sample, charset)?.let { charset to qualityScore(it, charset) } }
            .maxByOrNull { it.second }
            ?.first
            ?: Charset.forName("GB18030")
    }

    fun charsetForName(value: String?): Charset? = when (value?.uppercase()) {
        null, "", AUTO -> null
        "UTF-8", "UTF8" -> StandardCharsets.UTF_8
        "GBK", "GB2312", "GB18030" -> Charset.forName("GB18030")
        "BIG5", "BIG-5" -> Charset.forName("Big5")
        "UTF-16LE" -> StandardCharsets.UTF_16LE
        "UTF-16BE" -> StandardCharsets.UTF_16BE
        else -> runCatching { Charset.forName(value) }.getOrNull()
    }

    private fun decodeStrict(bytes: ByteArray, charset: Charset): String? = try {
        charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }

    /**
     * GB18030 can decode almost any byte sequence, including UTF-8 mojibake. Pick the candidate
     * that looks most like readable Chinese instead of treating "decodable" as "correct".
     */
    private fun qualityScore(text: String, charset: Charset): Double {
        if (text.isEmpty()) return 0.0
        var han = 0
        var punctuation = 0
        var controls = 0
        var privateUse = 0
        var replacements = 0
        var suspiciousLatin = 0
        text.forEach { char ->
            when {
                char == '\uFFFD' -> replacements++
                char.code in 0xE000..0xF8FF -> privateUse++
                char.isISOControl() && char !in setOf('\n', '\r', '\t') -> controls++
                Character.UnicodeScript.of(char.code) == Character.UnicodeScript.HAN -> han++
                char in "，。！？；：“”‘’《》、（）【】…—" -> punctuation++
                char in "ÃÂâ€ž™鐩楀浣滆" -> suspiciousLatin++
            }
        }
        val length = max(text.length, 1).toDouble()
        val commonChinese = listOf(
            "第一章", "第1章", "作者", "说道", "我们", "他们", "一个", "没有", "什么", "这里"
        ).count(text::contains)
        val mojibakeTokens = listOf(
            "锟斤拷", "烫烫烫", "ï»¿", "â€™", "â€œ", "â€", "鐩楀", "浣滆"
        ).count(text::contains)
        val utf8TieBreaker = if (charset == StandardCharsets.UTF_8) 0.35 else 0.0
        return (han / length) * 100.0 +
            (punctuation / length) * 22.0 +
            commonChinese * 1.6 +
            utf8TieBreaker -
            (controls / length) * 500.0 -
            (privateUse / length) * 900.0 -
            (replacements / length) * 1_200.0 -
            suspiciousLatin * 1.8 -
            mojibakeTokens * 20.0
    }
}
