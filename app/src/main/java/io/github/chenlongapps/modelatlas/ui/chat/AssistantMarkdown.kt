package io.github.chenlongapps.modelatlas.ui.chat

import android.content.ActivityNotFoundException
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.compose.MarkdownElement
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.NoOpImageTransformerImpl
import com.mikepenz.markdown.model.ReferenceLinkHandler
import com.mikepenz.markdown.model.State
import com.mikepenz.markdown.model.markdownAnimations
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.markdownDimens
import com.mikepenz.markdown.model.markdownPadding
import com.mikepenz.markdown.model.parseMarkdown
import com.mikepenz.markdown.model.rememberStreamingMarkdownState
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.BoldHighlight
import dev.snipme.highlights.model.ColorHighlight
import dev.snipme.highlights.model.SyntaxLanguage
import dev.snipme.highlights.model.SyntaxThemes
import io.github.chenlongapps.modelatlas.R
import io.github.chenlongapps.modelatlas.ui.modeldetail.isSafeDetailUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.html.HtmlGenerator

@Composable
internal fun AssistantMarkdown(reply: ChatReply, modifier: Modifier = Modifier) {
    val markdown by rememberAssistantMarkdown(reply)
    // 历史项重新入屏时等后台解析；仅保存高度，避免零高度把长回复内的阅读偏移截断。
    var measuredHeight by rememberSaveable { mutableIntStateOf(0) }
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val uriHandler = LocalUriHandler.current
    val safeUriHandler = remember(uriHandler) { assistantUriHandler(uriHandler) }
    val highlightCode = reply.status != ChatReplyStatus.STREAMING
    val components = remember(highlightCode) {
        markdownComponents(
            codeFence = { AssistantCodeBlock(it.node, it.content, it.typography.code, highlightCode) },
            codeBlock = { AssistantCodeBlock(it.node, it.content, it.typography.code, highlightCode) },
            table = {
                MarkdownTable(
                    it.content, it.node, it.typography.table,
                    headerBlock = { source, header, width, style ->
                        MarkdownTableHeader(source, header, width, style, maxLines = Int.MAX_VALUE)
                    },
                    rowBlock = { source, row, width, style ->
                        MarkdownTableRow(source, row, width, style, maxLines = Int.MAX_VALUE)
                    },
                )
            },
            image = { Text(imageAlt(it.content, it.node), style = it.typography.text) },
            custom = { type, model ->
                // 库也会把列表标记等交给这个回调；不要把结构标记显示成正文。
                if (type == MarkdownElementTypes.HTML_BLOCK || type == GFMElementTypes.BLOCK_MATH) {
                    Text(model.node.getTextInNode(model.content).toString(), style = model.typography.text)
                }
            },
        )
    }
    SelectionContainer(modifier) {
        CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
            Markdown(
                state = markdown,
                modifier = Modifier.fillMaxWidth(),
                colors = markdownColor(
                    text = scheme.onSurface,
                    codeBackground = scheme.surfaceContainerLow,
                    inlineCodeBackground = scheme.surfaceContainerHigh,
                    dividerColor = scheme.outlineVariant,
                    tableBackground = scheme.surfaceContainerLow,
                ),
                typography = markdownTypography(
                    h1 = type.titleLarge,
                    h2 = type.titleMedium,
                    h3 = type.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    h4 = type.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    h5 = type.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    h6 = type.bodyMedium.copy(fontWeight = FontWeight.Medium, color = scheme.onSurfaceVariant),
                    table = type.bodyMedium,
                    textLink = TextLinkStyles(
                        style = SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline),
                    ),
                ),
                padding = markdownPadding(
                    listItemTop = 1.dp, listItemBottom = 1.dp,
                    blockQuote = PaddingValues(horizontal = 8.dp),
                ),
                dimens = markdownDimens(tableCellWidth = 132.dp, tableCellPadding = 8.dp),
                components = components,
                imageTransformer = DisabledImages,
                annotator = AssistantAnnotator,
                animations = markdownAnimations(animateTextSize = { this }),
                loading = {
                    Spacer(it.height(with(LocalDensity.current) { measuredHeight.toDp() }))
                },
                success = { state, elements, layout ->
                    Column(layout.onSizeChanged { measuredHeight = it.height }) {
                        state.node.children.forEach { block ->
                            // 尾部块成为稳定块、或交接给最终 AST 时，保留同位置组件的交互状态。
                            key(block.startOffset, block.type) {
                                // 稳定块复用短原文和局部偏移，不为它保留旧整篇文本或重新排版。
                                val (node, source) = remember(block) {
                                    LocalMarkdownNode(block, block.startOffset) to
                                        state.content.substring(block.startOffset, block.endOffset)
                                }
                                MarkdownElement(node, elements, source)
                            }
                        }
                    }
                },
                error = { Text(reply.text, modifier = it.onSizeChanged { size -> measuredHeight = size.height }, style = type.bodyLarge) },
            )
        }
    }
}

private val DisabledImages = NoOpImageTransformerImpl()
private const val StreamBatchMillis = 32L
private val AssistantAnnotator = markdownAnnotator(markdownAnnotatorConfig(eolAsNewLine = true)) { content, node ->
    when (node.type) {
        MarkdownElementTypes.IMAGE -> append(imageAlt(content, node))
        MarkdownTokenTypes.HTML_TAG -> append(node.getTextInNode(content))
        else -> return@markdownAnnotator false
    }
    true
}

@Composable
private fun rememberAssistantMarkdown(reply: ChatReply): androidx.compose.runtime.State<State> {
    val latest by rememberUpdatedState(reply)
    var reset by remember { mutableIntStateOf(0) }
    val streaming = key(reset) { rememberStreamingMarkdownState() }
    val result = remember { mutableStateOf<State>(State.Loading()) }
    LaunchedEffect(streaming) {
        var appended = ""
        var rendered: ChatReply? = null
        var streamFailed = false
        var links = emptyMap<String, String?>()
        var linkHandler = SnapshotLinks(links)
        snapshotFlow { latest }.collect { observed ->
            if (observed == rendered) return@collect
            // 合并一小段时间内的快照；不取消在途解析，慢解析期间的新快照也自然合并。
            if (observed.status == ChatReplyStatus.STREAMING && appended.isNotEmpty()) delay(StreamBatchMillis)
            val current = latest
            if (current == rendered) return@collect
            val text = current.text
            if (current.status != ChatReplyStatus.STREAMING) {
                val final = withContext(Dispatchers.Default) { parseMarkdown(text) }
                if (latest == current) result.value = final
            } else if (rendered?.status?.let { it != ChatReplyStatus.STREAMING } == true) {
                result.value = State.Loading()
                reset++
                return@collect
            } else if (streamFailed) {
                // append 异常后内部游标可能已前进，不复用该状态；结束时仍尝试正常解析。
                if (!text.startsWith(appended)) {
                    result.value = State.Loading()
                    reset++
                    return@collect
                }
            } else {
                val next = withContext(Dispatchers.Default) {
                    if (!text.startsWith(appended)) null else try {
                        val snapshot = streaming.append(text.substring(appended.length))
                        val destinations = streaming.links.value
                        if (destinations != links) {
                            links = destinations
                            linkHandler = SnapshotLinks(links)
                        }
                        State.Success(
                            StreamingDocument(snapshot.stableAst + snapshot.unstableAstTail, text.length),
                            text,
                            linksLookedUp = true,
                            referenceLinkHandler = linkHandler,
                        )
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        State.Error(error)
                    } catch (error: StackOverflowError) {
                        State.Error(error)
                    }
                }
                if (next == null) {
                    result.value = State.Loading()
                    reset++
                    return@collect
                }
                appended = text
                streamFailed = next is State.Error
                // 若解析时切到了不同回复，不发布上次生成的尾部。
                if (latest.text.startsWith(text)) result.value = next
            }
            rendered = current
        }
    }
    return result
}

// UI 只读同一批次的不可变原文／AST／链接，不接触库里正被后台 append 的 StringBuilder 和映射。
private class StreamingDocument(
    override val children: List<ASTNode>,
    override val endOffset: Int,
) : ASTNode {
    override val type = MarkdownElementTypes.MARKDOWN_FILE
    override val startOffset = 0
    override val parent = null
}

private class LocalMarkdownNode(
    private val source: ASTNode,
    private val offset: Int,
    override val parent: ASTNode? = null,
) : ASTNode {
    override val type get() = source.type
    override val startOffset get() = source.startOffset - offset
    override val endOffset get() = source.endOffset - offset
    override val children: List<ASTNode> by lazy(LazyThreadSafetyMode.NONE) {
        source.children.map { LocalMarkdownNode(it, offset, this) }
    }
}

private class SnapshotLinks(links: Map<String, String?>) : ReferenceLinkHandler {
    private val destinations = links.mapKeys { it.key.lowercase() }
    override fun store(label: String, destination: String?) = Unit
    override fun find(label: String): String = destinations[label.lowercase()].orEmpty()
}

internal fun assistantUriHandler(delegate: UriHandler): UriHandler = object : UriHandler {
    override fun openUri(uri: String) {
        if (!isSafeDetailUrl(uri)) return
        try {
            delegate.openUri(uri)
        } catch (_: ActivityNotFoundException) {
        } catch (_: IllegalArgumentException) {
        } catch (_: SecurityException) {
        }
    }
}

private fun imageAlt(content: String, node: ASTNode): String =
    node.children.firstNotNullOfOrNull {
        it.findChildOfType(MarkdownElementTypes.LINK_TEXT) ?: it.findChildOfType(MarkdownElementTypes.LINK_LABEL)
    }
        ?.getTextInNode(content)?.toString()?.removeSurrounding("[", "]").orEmpty()

@Composable
private fun AssistantCodeBlock(node: ASTNode, source: String, style: TextStyle, highlight: Boolean) {
    val code = remember(node) { assistantCodeText(source, node) }
    val language = remember(node) {
        node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.getTextInNode(source)
            ?.trim()?.takeWhile { !it.isWhitespace() }?.toString()?.takeIf { it.isNotEmpty() }
    }
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surfaceContainerLow.luminance() < 0.5f
    val scroll = rememberScrollState()
    // 流中直接显示最新代码，不让高亮任务逐批重跑或用旧结果覆盖尾部。
    val highlighted = key(code, language, dark, highlight) {
        val highlighted by produceState<AnnotatedString?>(null) {
            if (highlight && language != null) {
                value = withContext(Dispatchers.Default) { highlightCode(code, language, dark) }
            }
        }
        highlighted
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (language != null) Text(
                    language, Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                CopyMessageButton(code, label = R.string.chat_copy_code)
            }
            Text(
                highlighted ?: remember(code) { AnnotatedString(code) },
                modifier = Modifier.horizontalScroll(scroll)
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                style = style, softWrap = false,
            )
        }
    }
}

/** 使用解析器提供的行节点，保留未闭合围栏的最后一行及代码缩进，不复制标记或引用前缀。 */
internal fun assistantCodeText(source: String, node: ASTNode): String {
    val fenced = node.type == MarkdownElementTypes.CODE_FENCE
    val indent = if (fenced) {
        node.children.firstOrNull()?.getTextInNode(source)?.takeWhile { it == ' ' }?.length ?: 0
    } else 4
    return buildString {
        var inBody = !fenced
        for (child in node.children) {
            when (child.type) {
                MarkdownTokenTypes.EOL -> {
                    if (inBody) append(child.getTextInNode(source))
                    inBody = true
                }
                MarkdownTokenTypes.CODE_FENCE_CONTENT, MarkdownTokenTypes.CODE_LINE ->
                    append(HtmlGenerator.trimIndents(child.getTextInNode(source), indent))
            }
        }
    }.removeSuffix("\n").removeSuffix("\r")
}

private fun highlightCode(code: String, language: String, dark: Boolean): AnnotatedString? {
    val syntax = SyntaxLanguage.getByName(language) ?: return null
    return try {
        val highlights = Highlights.Builder().code(code).language(syntax)
            .theme(SyntaxThemes.default(darkMode = dark)).build().getHighlights()
        buildAnnotatedString {
            append(code)
            for (highlight in highlights) {
                val (start, end) = highlight.location
                if (start < 0 || end < start || end > code.length) continue
                val style = when (highlight) {
                    is BoldHighlight -> SpanStyle(fontWeight = FontWeight.Bold)
                    is ColorHighlight -> SpanStyle(color = Color(highlight.rgb).copy(alpha = 1f))
                }
                addStyle(style, start, end)
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } catch (_: StackOverflowError) {
        null
    }
}
