package io.github.chenlongapps.modelatlas.ui.chat

import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.parser.EmptyStreamingMarkdownFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantMarkdownTest {
    @Test fun cjkPunctuationKeepsBoldAndItalicBoundariesWithoutEditingSource() {
        val strong = MarkdownElementTypes.STRONG
        val italic = MarkdownElementTypes.EMPH
        val samples = listOf(
            Triple("**内容）**后续文字", "**内容）**", strong),
            Triple("**内容）**", "**内容）**", strong),
            Triple("正文**（重点）**后续", "**（重点）**", strong),
            Triple("前文*「日本語」*かな", "*「日本語」*", italic),
            Triple("앞말__（강조）__다음", "__（강조）__", strong),
            Triple("_注意！_後文", "_注意！_", italic),
            Triple("**结论）**\n下一行", "**结论）**", strong),
            Triple("**重点：**𠮷", "**重点：**", strong),
            Triple("正文**结论。**下一句", "**结论。**", strong),
        )
        for ((text, marked, type) in samples) {
            val parsed = parseAssistant(text)
            assertEquals(text, parsed.content)
            assertEquals(text.length, parsed.node.endOffset)
            val emphasis = requireNotNull(parsed.node.descendants().singleOrNull { it.type == type }) {
                "Expected one $type node for $text"
            }
            assertEquals(text.indexOf(marked), emphasis.startOffset)
            assertEquals(marked, emphasis.getTextInNode(text).toString())
        }
    }

    @Test fun compatibilityDoesNotChangeProtectedSyntaxOrStealExistingPairs() {
        val unchanged = listOf(
            "```md\n**内容）**后文\n```",
            "> ~~~\n> **内容）**后文",
            "    **内容）**后文",
            "`` ` **内容）**后文 ``",
            "\\*\\*内容）\\*\\*后文",
            "\\*内容）*后文",
            "普通文字 *未闭合",
            "前文_词语_后文",
            "**粗体**、*斜体*、~~删除~~、***嵌套***",
            "**`原样）`**后文 [**链接**](https://example.test)",
            "**内容）**后文**", // 标准配对是 **后文**。
            "**前部**中间**内容）**后文**",
            "[链接](<https://example.test/**内容）**后文> \"**提示）**后文\")",
            "<https://example.test/**内容）**后文>",
            "[标签][文档]\n\n[文档]: <https://example.test/**内容）**后文>",
            "<span title=\"**内容）**后文\">原文</span>",
        )
        for (text in unchanged) {
            val standard = parseMarkdown(text) as State.Success
            val compatible = parseAssistant(text)
            assertEquals(text, listOf(standard.node).structure(), listOf(compatible.node).structure())
            assertEquals(standard.referenceLinkHandler.find("文档"), compatible.referenceLinkHandler.find("文档"))
        }
    }

    @Test fun streamingReparsesTheTailAtEveryChunkBoundaryAndKeepsStableBlocks() {
        val text = "## 历史🧭\n\n**内容）**后续\n\n```md\n**原样）**后文\n```\n\n*「日本語」*かな"
        val file = EmptyStreamingMarkdownFile(AssistantMarkdownFlavour)
        var stable = emptyList<ASTNode>()
        val compatible = parseAssistant(text)
        val stableBoldOffset = text.indexOf("**内容")

        for (end in 1..text.length) {
            file.append(text.substring(end - 1, end))
            val prefix = text.substring(0, end)
            val blocks = file.stableChildren + file.unstableTail
            assertEquals(prefix, parseAssistant(prefix).node.children.structure(), blocks.structure())
            stable.forEachIndexed { index, block -> assertSame(block, file.stableChildren[index]) }
            stable = file.stableChildren.toList()
        }

        assertTrue(stable.flatMap { it.descendants().toList() }.any {
            it.type == MarkdownElementTypes.STRONG && it.startOffset == stableBoldOffset
        })
        assertEquals(compatible.node.children.structure(), (file.stableChildren + file.unstableTail).structure())
    }

    @Test fun codeCopyUsesAstLinesAndPreservesIndentationEvenBeforeTheClosingFence() {
        val code = "  first()\n\n    next()"
        val unclosed = "```kotlin\n$code"
        assertEquals(code, codeFrom(unclosed))
        assertEquals(code, codeFrom("$unclosed\n```"))
        assertEquals("    next()\n", codeFrom("```\n    next()\n\n```"))
        assertEquals("first()\n  next()", codeFrom("    first()\n      next()"))
        assertEquals("first()\n  next()", codeFrom("> ```\n> first()\n>   next()\n> ```"))
    }

    @Test fun onlyAbsoluteHttpAndHttpsLinksWithoutCredentialsReachTheSystemHandler() {
        val opened = mutableListOf<String>()
        val handler = assistantUriHandler(object : UriHandler {
            override fun openUri(uri: String) { opened += uri }
        })
        val safe = listOf("https://example.test/a?q=x#ref", "http://example.test", "HTTPS://example.test")
        val unsafe = listOf(
            "javascript:alert(1)", "file:///data/local/tmp/a", "content://example.test/a",
            "intent://example.test", "data:text/html,hello", "mailto:user@example.test",
            "//example.test", "/relative", "https:relative", "https://user:pass@example.test/",
            "https://example.test/a b", " https://example.test/",
        )
        (safe + unsafe).forEach(handler::openUri)
        assertEquals(safe, opened)
    }

    private fun codeFrom(source: String): String {
        val node = parseAssistant(source).node.descendants().first {
            it.type == MarkdownElementTypes.CODE_FENCE || it.type == MarkdownElementTypes.CODE_BLOCK
        }
        return assistantCodeText(source, node)
    }

    private fun parseAssistant(text: String) = parseMarkdown(text, flavour = AssistantMarkdownFlavour) as State.Success

    private fun ASTNode.descendants(): Sequence<ASTNode> = sequence {
        yield(this@descendants)
        children.forEach { yieldAll(it.descendants()) }
    }

    private data class NodeShape(val type: IElementType, val start: Int, val end: Int, val children: List<NodeShape>)

    private fun List<ASTNode>.structure(): List<NodeShape> =
        map { NodeShape(it.type, it.startOffset, it.endOffset, it.children.structure()) }
}
