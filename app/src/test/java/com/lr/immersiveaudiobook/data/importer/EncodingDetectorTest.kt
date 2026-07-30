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
}
