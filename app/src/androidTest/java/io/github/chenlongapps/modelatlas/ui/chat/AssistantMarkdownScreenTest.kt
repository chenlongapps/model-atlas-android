package io.github.chenlongapps.modelatlas.ui.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performFirstLinkClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.chenlongapps.modelatlas.ui.theme.ModelAtlasTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AssistantMarkdownScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rendersGfmAndShowsImagesAndHtmlOnlyAsText() {
        val source = """
            # 一级标题

            ### 三级标题

            **粗体**、*斜体*、~~删除线~~、`inline`  
            下一行

            1. 有序项

            - 无序项

            > 引用内容

            ---

            | 列名 | 值 |
            | --- | --- |
            | 单元格 | **重点** |

            [文档](https://example.test)

            ![图片描述](https://example.test/image.png)

            <script>alert(1)</script>
        """.trimIndent()
        var dark by mutableStateOf(false)
        compose.setContent {
            ModelAtlasTheme(darkTheme = dark) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AssistantMarkdown(ChatReply(source, ChatReplyStatus.COMPLETE))
                }
            }
        }
        awaitText("一级标题")
        compose.onNodeWithText("一级标题").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        for (text in listOf("三级标题", "粗体", "斜体", "删除线", "inline", "下一行", "有序项", "无序项", "引用内容", "单元格", "重点", "文档", "图片描述", "<script>alert(1)</script>")) {
            compose.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("https://example.test/image.png", substring = true).assertDoesNotExist()
        compose.onNodeWithText("-").assertDoesNotExist()
        compose.runOnIdle { dark = true }
        compose.onNodeWithText("一级标题").performScrollTo().assertIsDisplayed()
    }

    @Test fun batchesStreamingFencesAndTablesAndKeepsHistoryIndependent() {
        var reply by mutableStateOf(ChatReply("## 新回复\n\n```kotlin\nfun first() = 1", ChatReplyStatus.STREAMING))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    AssistantMarkdown(ChatReply("# 历史回复", ChatReplyStatus.COMPLETE))
                    AssistantMarkdown(reply)
                }
            }
        }
        awaitText("fun first() = 1")
        compose.onNodeWithText("复制代码").assertIsDisplayed()
        val closed = reply.text + "\nfun second() = 2\n```\n\n- 列表尾项\n\n| 标题 |"
        compose.runOnIdle { reply = reply.copy(text = closed) }
        awaitText("fun first() = 1\nfun second() = 2")
        compose.onNodeWithText("列表尾项", substring = true).performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            reply = ChatReply("$closed\n| --- |\n| 表格尾项 |", ChatReplyStatus.STREAMING)
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("表格尾项", substring = true)).fetchSemanticsNodes().size == 1 }
        compose.runOnIdle { reply = reply.copy(text = reply.text + "\n\n结束", status = ChatReplyStatus.COMPLETE) }
        awaitText("结束")
        compose.onNodeWithText("历史回复").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("新回复").assertIsDisplayed()
    }

    @Test fun cjkEmphasisSurvivesSplitMarkersAndCompletionWithoutVisibleSpaces() {
        val closed = "**内容）**"
        val literal = "**代码）**后文"
        var reply by mutableStateOf(ChatReply(closed.dropLast(1), ChatReplyStatus.STREAMING))
        compose.setContent {
            ModelAtlasTheme(dynamicColor = false) {
                Column(Modifier.width(280.dp).verticalScroll(rememberScrollState())) {
                    AssistantMarkdown(reply)
                }
            }
        }
        awaitText("内容）", substring = true)
        compose.runOnIdle { reply = reply.copy(text = closed) }
        assertEmphasis("内容）", 0, 3, bold = true)
        compose.runOnIdle { reply = reply.copy(text = "${closed}后续") }
        assertEmphasis("内容）后续", 0, 3, bold = true)

        val streamed = "${closed}后续\n\n正文*「日本語」*かな\n\n```md\n$literal"
        compose.runOnIdle { reply = reply.copy(text = streamed) }
        assertEmphasis("正文「日本語」かな", 2, 7, bold = false)
        awaitText(literal)
        compose.runOnIdle { reply = ChatReply("$streamed\n```\n\n已完成", ChatReplyStatus.COMPLETE) }
        awaitText("已完成")
        assertEmphasis("内容）后续", 0, 3, bold = true)
        assertEmphasis("正文「日本語」かな", 2, 7, bold = false)
        compose.onNodeWithText(literal).performScrollTo().assertIsDisplayed()
    }

    @Test fun wideContentStaysInsideTheReplyAndUsesIndependentHorizontalScroll() {
        val longLink = "https://example.test/" + "a".repeat(510)
        val table = List(10) { "列$it" }.joinToString(" | ", "| ", " |") + "\n" +
            List(10) { "---" }.joinToString(" | ", "| ", " |") + "\n" +
            List(10) { "值$it" }.joinToString(" | ", "| ", " |")
        val code = "x".repeat(500)
        compose.setContent {
            ModelAtlasTheme(darkTheme = true) {
                Column(Modifier.width(280.dp).verticalScroll(rememberScrollState()).testTag("narrow-chat")) {
                    AssistantMarkdown(ChatReply("```\n$code\n```\n\n$table\n\n<$longLink>", ChatReplyStatus.COMPLETE), Modifier.testTag("reply"))
                }
            }
        }
        awaitText(code)
        val chat = compose.onNodeWithTag("narrow-chat").fetchSemanticsNode().boundsInRoot
        val reply = compose.onNodeWithTag("reply").fetchSemanticsNode().boundsInRoot
        assertTrue(reply.left >= chat.left && reply.right <= chat.right)
        val horizontal = compose.onAllNodes(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.HorizontalScrollAxisRange) and hasAnyAncestor(hasTestTag("reply")),
        ).fetchSemanticsNodes()
        assertEquals(2, horizontal.size)
        horizontal.forEach { assertTrue(it.config[SemanticsProperties.HorizontalScrollAxisRange].maxValue() > 0f) }
        compose.onNodeWithText(longLink).performScrollTo().assertIsDisplayed()
    }

    @Test fun linksOnlyReachTheExternalHandlerAfterAnAllowedClick() {
        val urls = mutableListOf<String>()
        val handler = object : UriHandler {
            override fun openUri(uri: String) { urls += uri }
        }
        val safe = "https://example.test/path?a=b#section"
        val source = "[前置引用][doc]\n\n[本机文件](file:///data/local/tmp/example)\n\n[doc]: $safe"
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides handler) {
                ModelAtlasTheme { AssistantMarkdown(ChatReply(source, ChatReplyStatus.COMPLETE)) }
            }
        }
        awaitText("前置引用")
        compose.runOnIdle { assertTrue(urls.isEmpty()) }
        compose.onNodeWithText("本机文件").performFirstLinkClick()
        compose.onNodeWithText("前置引用").performFirstLinkClick()
        compose.runOnIdle { assertEquals(listOf(safe), urls) }
    }

    private fun awaitText(text: String, substring: Boolean = false) {
        compose.waitUntil(5_000) { compose.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().size == 1 }
    }

    private fun assertEmphasis(text: String, start: Int, end: Int, bold: Boolean) {
        awaitText(text)
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val styles = layouts.single().layoutInput.text.spanStyles.filter {
            if (bold) it.item.fontWeight == FontWeight.Bold else it.item.fontStyle == FontStyle.Italic
        }
        assertEquals(listOf(start to end), styles.map { it.start to it.end })
    }
}
