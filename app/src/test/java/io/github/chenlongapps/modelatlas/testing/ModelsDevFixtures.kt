package io.github.chenlongapps.modelatlas.testing

/** 当前服务商接口的合成样本，由解析、HTTP 契约和持久化映射测试共用。 */
internal fun modelsDevProvidersJson(): String =
    requireNotNull(object {}.javaClass.getResource("/modelsdev/api.json")).readText()
