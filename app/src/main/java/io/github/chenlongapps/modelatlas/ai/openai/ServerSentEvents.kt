package io.github.chenlongapps.modelatlas.ai.openai

import io.github.chenlongapps.modelatlas.ai.LlmException
import java.io.EOFException
import okio.BufferedSource

/** 只消费 SSE data；支持 UTF-8、LF/CRLF、多行、注释及非 data 字段。事件限长避免无限缓冲。 */
internal class ServerSentEvents(private val source: BufferedSource) {
    private var firstLine = true

    fun nextData(): String? {
        val data = mutableListOf<String>()
        var bytes = 0L
        while (!source.exhausted()) {
            val raw = try {
                source.readUtf8LineStrict(limit = MAX_EVENT_BYTES)
            } catch (_: EOFException) {
                throw LlmException.Protocol()
            }
            val line = if (firstLine) raw.removePrefix("\uFEFF") else raw
            firstLine = false
            bytes += line.toByteArray(Charsets.UTF_8).size + 1
            if (bytes > MAX_EVENT_BYTES) throw LlmException.Protocol()
            if (line.isEmpty()) {
                if (data.isNotEmpty()) return data.joinToString("\n")
                bytes = 0
                continue
            }
            val field = line.substringBefore(':')
            if (field == "data") data += line.substringAfter(':', "").removePrefix(" ")
        }
        // 没有空行就未完成派发，不能把半个事件视为有效 JSON。
        if (data.isNotEmpty()) throw LlmException.Protocol()
        return null
    }

    private companion object {
        const val MAX_EVENT_BYTES = 1024L * 1024
    }
}
