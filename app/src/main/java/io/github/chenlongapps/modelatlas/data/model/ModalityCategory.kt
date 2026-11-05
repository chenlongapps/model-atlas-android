package io.github.chenlongapps.modelatlas.data.model

import java.util.Locale

private val simpleModalityCategories = setOf("text", "image", "audio", "video", "pdf")

/** 展示／筛选使用模态类别；DTO 与数据库仍保留来源的具体 MIME 格式。 */
fun String.toModalityCategory(): String {
    val type = substringBefore(';').trim().lowercase(Locale.ROOT)
    return when {
        type in simpleModalityCategories -> type
        type == "application/pdf" -> "pdf"
        // 只识别来源明确给出的媒体类型，不从扩展名或未知 application 类型推断能力。
        type.substringAfter('/', "").isNotBlank() -> when (type.substringBefore('/')) {
            "text" -> "text"
            "image" -> "image"
            "audio" -> "audio"
            "video" -> "video"
            else -> this
        }
        else -> this
    }
}

/** 按首次出现顺序合并同类格式；缺失、空集合与未知值保持原有语义。 */
fun List<String>?.toModalityCategories(): List<String>? = this?.map { it.toModalityCategory() }?.distinct()
