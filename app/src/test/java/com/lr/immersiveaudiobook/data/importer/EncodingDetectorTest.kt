package com.lr.immersiveaudiobook.data.importer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset
import kotlin.io.path.createTempFile

class EncodingDetectorTest {
    @Test
    fun detectsUtf8() {
        val file = createTempFile("utf8", ".txt").toFile()
        file.writeText("第一章\n这是一段中文。", Charsets.UTF_8)
        assertEquals(Charsets.UTF_8, EncodingDetector.detect(file))
        file.delete()
    }

    @Test
    fun fallsBackToGb18030ForGbkText() {
        val file = createTempFile("gbk", ".txt").toFile()
        file.writeBytes("第一章\n这是一段中文。".toByteArray(Charset.forName("GBK")))
        assertEquals("GB18030", EncodingDetector.detect(file).name())
        file.delete()
    }

    @Test
    fun detectsEachFileIndependentlyInAMixedEncodingBatch() {
        val utf8 = createTempFile("mixed-utf8", ".txt").toFile()
        val gbk = createTempFile("mixed-gbk", ".txt").toFile()
        utf8.writeText("序章\n夜色里传来脚步声。", Charsets.UTF_8)
        gbk.writeBytes(
            "================\r\n第一章\r\n他低声说道：“别动。”".toByteArray(
                Charset.forName("GBK")
            )
        )

        assertEquals(Charsets.UTF_8, EncodingDetector.detect(utf8))
        assertEquals("GB18030", EncodingDetector.detect(gbk).name())
        utf8.delete()
        gbk.delete()
    }

    @Test
    fun honorsManualEncodingOverride() {
        val file = createTempFile("override", ".txt").toFile()
        file.writeText("第一章\n测试", Charsets.UTF_8)
        assertEquals("GB18030", EncodingDetector.detect(file, "GBK").name())
        file.delete()
    }
}
