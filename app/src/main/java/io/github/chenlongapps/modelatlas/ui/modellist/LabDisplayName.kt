package io.github.chenlongapps.modelatlas.ui.modellist

/** 仅用于展示的品牌名称；筛选、排序和 logo 匹配继续使用原始 LAB ID。 */
private val LabDisplayNames: Map<String, String> = mapOf(
    "ai21" to "AI21 Labs",
    "alibaba" to "Alibaba",
    "anthropic" to "Anthropic",
    "cohere" to "Cohere",
    "deepseek" to "DeepSeek",
    "google" to "Google",
    "inception" to "Inception",
    "meta" to "Meta",
    "minimax" to "MiniMax",
    "mistral" to "Mistral AI",
    "moonshotai" to "Moonshot AI",
    "nvidia" to "NVIDIA",
    "openai" to "OpenAI",
    "perplexity" to "Perplexity",
    "poolside" to "poolside",
    "reflection" to "Reflection",
    "stepfun" to "StepFun",
    "thinkingmachines" to "Thinking Machines",
    "vispark" to "Vispark",
    "vivgrid" to "Vivgrid",
    "xai" to "xAI",
    "xiaomi" to "Xiaomi",
    "zhipuai" to "Zhipu AI",
)

/** 未收录的 LAB 保留原文，不用首字母大写猜测品牌拼写。 */
internal fun labDisplayName(lab: String): String = LabDisplayNames[lab] ?: lab
