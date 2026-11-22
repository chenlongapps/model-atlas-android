package io.github.chenlongapps.modelatlas.ui.chat

import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.HighlightDelimiterParser
import org.intellij.markdown.flavours.gfm.StrikeThroughDelimiterParser
import org.intellij.markdown.parser.sequentialparsers.DelimiterParser
import org.intellij.markdown.parser.sequentialparsers.EmphasisLikeParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParserManager
import org.intellij.markdown.parser.sequentialparsers.TokensCache
import org.intellij.markdown.parser.sequentialparsers.impl.EmphStrongDelimiterParser

internal object AssistantMarkdownFlavour : GFMFlavourDescriptor() {
    override val sequentialParserManager = object : SequentialParserManager() {
        private val parsers = super@AssistantMarkdownFlavour.sequentialParserManager.getParserSequence().map {
            if (it is EmphasisLikeParser) CjkEmphasis(it) else it
        }

        override fun getParserSequence() = parsers
    }
}

private class CjkEmphasis(private val standard: SequentialParser) : SequentialParser {
    override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
        // 块解析、代码和链接解析先行；只在剩余的强调分隔符配对前调整边界，不改源字符。
        val emphasis = CjkEmphasisDelimiters()
        val compatible = EmphasisLikeParser(emphasis, StrikeThroughDelimiterParser(), HighlightDelimiterParser())
            .parse(tokens, rangesToGlue)
        if (!emphasis.adjusted) return compatible

        // 兼容配对不能抢占、扩大或缩小标准 GFM 已识别的强调等样式。
        val original = standard.parse(tokens, rangesToGlue)
        val nodes = compatible.parsedNodes.toHashSet()
        return if (nodes.containsAll(original.parsedNodes)) compatible else original
    }
}

private val StandardEmphasis = EmphStrongDelimiterParser()

private class CjkEmphasisDelimiters : DelimiterParser() {
    var adjusted = false
        private set

    override fun scan(tokens: TokensCache, iterator: TokensCache.Iterator, delimiters: MutableList<Info>): Int {
        val first = delimiters.size
        val count = StandardEmphasis.scan(tokens, iterator, delimiters)
        if (count == 0) return 0

        val start = iterator.start
        val end = start + count // 每个未转义的 * 或 _ 占一个 UTF-16 字符。
        val info = delimiters[first]
        val open = !info.canOpen && tokens.getRawCharAt(end) in CjkOpening && tokens.isCjkAt(start - 1)
        val close = !info.canClose && tokens.getRawCharAt(start - 1) in CjkClosing && tokens.isCjkAt(end)
        if (open || close) {
            adjusted = true
            for (index in first until delimiters.size) {
                if (open) delimiters[index].canOpen = true
                if (close) delimiters[index].canClose = true
            }
        }
        return count
    }

    override fun process(
        tokens: TokensCache,
        iterator: TokensCache.Iterator,
        delimiters: MutableList<Info>,
        result: SequentialParser.ParsingResultBuilder,
    ) = StandardEmphasis.process(tokens, iterator, delimiters, result)
}

private const val CjkOpening = "（［｛〈《「『【〔〖〘〚“‘｟｢"
private const val CjkClosing = "）］｝〉》」』】〕〗〙〛”’｠｣、。，．！？：；…｡､"

private fun TokensCache.isCjkAt(index: Int): Boolean {
    val char = getRawCharAt(index)
    val point = when {
        char.isHighSurrogate() && getRawCharAt(index + 1).isLowSurrogate() ->
            Character.toCodePoint(char, getRawCharAt(index + 1))
        char.isLowSurrogate() && getRawCharAt(index - 1).isHighSurrogate() ->
            Character.toCodePoint(getRawCharAt(index - 1), char)
        char == 'ー' || char == 'ｰ' -> return true
        !char.isLetter() -> return false
        else -> char.code
    }
    return when (Character.UnicodeScript.of(point)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL, Character.UnicodeScript.BOPOMOFO -> true
        else -> false
    }
}
