package io.github.chenlongapps.modelatlas.data.model

/**
 * 报价服务商的选取约定：模型开发方的第一方服务优先，否则按内置的第三方优先级选一家。
 *
 * 该顺序是**应用内的报价展示顺序**，不是质量、速度、可用性或最低价排行榜；
 * 未列入 [thirdPartyPriority] 的服务商排在所有已列入者之后，再按服务商 ID 稳定排序，
 * 因此同一个模型在不同目录快照下会得到稳定的展示结果。
 */
object PriceProviderPolicy {
    /**
     * 模型 LAB（稳定 ID 第一个 `/` 前的部分）→ 该开发方第一方 API 服务商在 api.json 中的键。
     *
     * 2026-10-19 对照上游 api.json 核实：每个键对应的服务商都存在、且确有通过显式
     * `canonical_model_id` 关联到该 LAB 的模型服务与报价。LAB 本身不构成官方认定，
     * 未列入的 LAB 一律没有官方报价，直接走第三方优先级，不按名称或前缀猜测。
     */
    private val officialProviderIdByLab: Map<String, String> = mapOf(
        "ai21" to "ai21",
        "alibaba" to "alibaba",
        "amazon" to "amazon-bedrock",
        "anthropic" to "anthropic",
        "arcee-ai" to "arcee",
        "cohere" to "cohere",
        "deepseek" to "deepseek",
        "google" to "google",
        "meta" to "meta",
        "mistral" to "mistral",
        "moonshotai" to "moonshotai",
        "nvidia" to "nvidia",
        "openai" to "openai",
        "poolside" to "poolside",
        "reflection" to "reflection",
        "sakana" to "sakana",
        "stepfun" to "stepfun",
        "thinkingmachines" to "thinkingmachines",
        "vivgrid" to "vivgrid",
        "xai" to "xai",
        "zhipuai" to "zhipuai",
    )

    /** 没有官方报价时按此顺序挑选第三方报价；顺序是产品约定，可随数据源变化调整。 */
    val thirdPartyPriority: List<String> = listOf(
        "openrouter",
        "togetherai",
        "fireworks-ai",
        "deepinfra",
        "siliconflow",
        "novita-ai",
        "huggingface",
        "amazon-bedrock",
        "google-vertex",
        "azure",
    )

    /** 该 LAB 的第一方服务商键；没有已核实的对应关系时返回 null。 */
    fun officialProviderId(lab: String?): String? = lab?.let { officialProviderIdByLab[it] }

    /** 第三方优先级序号；未列入者返回列表长度，即排在所有已列入者之后。 */
    fun thirdPartyRank(providerId: String): Int =
        thirdPartyPriority.indexOf(providerId).takeIf { it >= 0 } ?: thirdPartyPriority.size
}
