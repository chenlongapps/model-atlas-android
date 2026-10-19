package io.github.chenlongapps.modelatlas.data.remote

import kotlinx.serialization.json.Json

/** 纯解析入口与 Retrofit 使用相同的严格字段类型、宽容未知字段配置。 */
val ModelsDevJson = Json { ignoreUnknownKeys = true }
