package com.lr.immersiveaudiobook.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAnalysisTest {
    @Test
    fun detectsCommonChineseChapterTitles() {
        assertTrue(ChapterDetector.isChapterTitle("第一章 七星疑云"))
        assertTrue(ChapterDetector.isChapterTitle("第12章 地下室"))
        assertTrue(ChapterDetector.isChapterTitle("卷一 旧城"))
        assertTrue(ChapterDetector.isChapterTitle("楔子"))
        assertTrue(ChapterDetector.isChapterTitle("番外篇 二"))
        assertFalse(ChapterDetector.isChapterTitle("他翻到第一章，却没有继续看。"))
    }

    @Test
    fun splitsSentencesAndAssignsEmotion() {
        val result = SentenceSegmenter.segment("他低声说：“别动！”走廊里传来脚步声。")
        assertEquals(2, result.size)
        assertTrue(result.first().isDialogue)
        assertEquals("低声耳语", result.first().emotion)
        assertEquals("平静", result.last().emotion)
    }

    @Test
    fun identifiesQuestionEmotion() {
        assertEquals("疑惑", EmotionAnalyzer.analyze("你确定吗？"))
    }
}
