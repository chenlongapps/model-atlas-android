package io.github.chenlongapps.modelatlas.ui.chat

import androidx.compose.ui.platform.UriHandler
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.parseMarkdown
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.junit.Assert.assertEquals
import org.junit.Test

class AssistantMarkdownTest {
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
        val root = (parseMarkdown(source) as State.Success).node
        fun ASTNode.codeNode(): ASTNode? =
            if (type == MarkdownElementTypes.CODE_FENCE || type == MarkdownElementTypes.CODE_BLOCK) this
            else children.firstNotNullOfOrNull { it.codeNode() }
        return assistantCodeText(source, checkNotNull(root.codeNode()))
    }
}
