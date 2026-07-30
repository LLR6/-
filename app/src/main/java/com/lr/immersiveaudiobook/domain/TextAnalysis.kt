package com.lr.immersiveaudiobook.domain

data class AnalyzedSentence(
    val text: String,
    val isDialogue: Boolean,
    val characterName: String,
    val emotion: String,
    val pauseAfterMs: Int
)

object ChapterDetector {
    private val patterns = listOf(
        Regex("""^\s*第[0-9零一二三四五六七八九十百千万两〇]+[章节回卷部篇集]\s*.*$"""),
        Regex("""^\s*[卷部篇][一二三四五六七八九十百千万两〇0-9]+\s*.*$"""),
        Regex("""^\s*(序章|序言|前言|楔子|引子|后记|尾声|终章|番外(?:篇)?(?:\s*[0-9一二三四五六七八九十]*)?)\s*.*$"""),
        Regex("""^\s*(Chapter|CHAPTER)\s+\d+\b.*$""", RegexOption.IGNORE_CASE)
    )

    fun isChapterTitle(line: String): Boolean {
        val value = line.trim()
        if (value.isBlank() || value.length > 80) return false
        return patterns.any { it.matches(value) }
    }
}

object DialogueDetector {
    private val quotePairs = listOf('“' to '”', '"' to '"', '「' to '」', '『' to '』', '‘' to '’')
    private val speakerBefore = Regex(
        """([\p{IsHan}A-Za-z0-9·]{1,12})(?:低声|沉声|厉声|轻声|大声|缓缓|突然)?(?:说道|说|问道|问|答道|答|喊道|喊|叫道|道|吼道|嘀咕|笑道|哭道)[:：,，]?\s*$"""
    )
    private val speakerAfter = Regex(
        """^\s*[,，]?\s*([\p{IsHan}A-Za-z0-9·]{1,12})(?:低声|沉声|厉声|轻声|大声|缓缓)?(?:说道|说|问道|问|答道|答|喊道|喊|叫道|道|吼道)"""
    )

    fun containsDialogue(text: String): Boolean =
        quotePairs.any { (open, close) ->
            val first = text.indexOf(open)
            first >= 0 && text.indexOf(close, first + 1) > first
        }

    fun guessSpeaker(fullText: String, dialogueStart: Int, dialogueEnd: Int): String {
        val before = fullText.substring(0, dialogueStart).takeLast(80)
        val after = fullText.substring((dialogueEnd + 1).coerceAtMost(fullText.length)).take(60)
        return speakerBefore.find(before)?.groupValues?.getOrNull(1)
            ?: speakerAfter.find(after)?.groupValues?.getOrNull(1)
            ?: "默认角色"
    }
}

object EmotionAnalyzer {
    private val rules = linkedMapOf(
        "恐惧" to listOf("恐惧", "害怕", "惊恐", "毛骨悚然", "冷汗", "颤抖", "鬼", "尸"),
        "紧张" to listOf("紧张", "危险", "小心", "急忙", "猛然", "屏住呼吸", "追来"),
        "愤怒" to listOf("愤怒", "怒道", "吼道", "咬牙", "混蛋", "可恶"),
        "悲伤" to listOf("悲伤", "哭", "眼泪", "哽咽", "失去", "绝望"),
        "神秘" to listOf("神秘", "诡异", "秘密", "古老", "谜", "黑暗"),
        "压迫" to listOf("压迫", "窒息", "沉重", "逼近", "死寂"),
        "激动" to listOf("激动", "兴奋", "终于", "成功"),
        "轻松" to listOf("轻松", "微笑", "笑了", "放心"),
        "幽默" to listOf("哈哈", "开玩笑", "打趣", "滑稽")
    )

    fun analyze(text: String): String {
        if (Regex("""[喊吼叫][道着]?[！!]|[！!]{2,}""").containsMatchIn(text)) return "大声喊叫"
        if (listOf("低声", "耳语", "悄声", "小声").any(text::contains)) return "低声耳语"
        if (text.contains("？") || text.contains("?")) return "疑惑"
        return rules.entries.firstOrNull { (_, words) -> words.any(text::contains) }?.key ?: "平静"
    }

    fun pauseAfter(text: String, emotion: String): Int = when {
        emotion == "恐惧" || emotion == "神秘" -> 520
        emotion == "紧张" || emotion == "急促" -> 150
        text.endsWith("……") || text.endsWith("…") -> 700
        text.endsWith("。") || text.endsWith("！") || text.endsWith("？") -> 320
        else -> 220
    }
}

object SentenceSegmenter {
    private val terminators = setOf('。', '！', '？', '!', '?', '；', ';')
    private val closers = setOf('”', '"', '’', '」', '』', '）', ')', '】', ']')

    fun segment(paragraph: String): List<AnalyzedSentence> {
        val normalized = paragraph.trim().replace(Regex("""[ \t]+"""), " ")
        if (normalized.isBlank()) return emptyList()

        val pieces = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < normalized.length) {
            val char = normalized[index]
            current.append(char)
            if (char in terminators || char == '…' && normalized.getOrNull(index + 1) == '…') {
                if (char == '…') {
                    current.append(normalized[index + 1])
                    index++
                }
                var next = index + 1
                while (next < normalized.length && normalized[next] in closers) {
                    current.append(normalized[next])
                    next++
                }
                pieces += current.toString().trim()
                current.clear()
                index = next - 1
            }
            index++
        }
        if (current.isNotBlank()) pieces += current.toString().trim()

        return pieces.filter { it.isNotBlank() }.map { sentence ->
            val isDialogue = DialogueDetector.containsDialogue(sentence) ||
                (sentence.firstOrNull() in setOf('“', '"', '「', '『', '‘'))
            val firstQuote = sentence.indexOfFirst { it in setOf('“', '"', '「', '『', '‘') }
            val lastQuote = sentence.indexOfLast { it in setOf('”', '"', '」', '』', '’') }
            val speaker = if (isDialogue) {
                DialogueDetector.guessSpeaker(
                    sentence,
                    firstQuote.coerceAtLeast(0),
                    lastQuote.coerceAtLeast(firstQuote.coerceAtLeast(0))
                )
            } else {
                "旁白"
            }
            val emotion = EmotionAnalyzer.analyze(sentence)
            AnalyzedSentence(
                text = sentence,
                isDialogue = isDialogue,
                characterName = speaker,
                emotion = emotion,
                pauseAfterMs = EmotionAnalyzer.pauseAfter(sentence, emotion)
            )
        }
    }
}
