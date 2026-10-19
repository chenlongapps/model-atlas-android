package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.ModelListRow
import io.github.chenlongapps.modelatlas.data.local.ModelOfferingPriceRow
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.model.LlmInclusionPolicy
import io.github.chenlongapps.modelatlas.data.model.ModelPriceSummary
import io.github.chenlongapps.modelatlas.data.model.ModelPriceTierSummary
import io.github.chenlongapps.modelatlas.data.model.ModelSummary
import io.github.chenlongapps.modelatlas.data.model.PriceProviderPolicy

/**
 * 输入来自按名称和稳定 ID 排序的左连接；缺少服务商时仍保留规范模型。
 *
 * 输出按发布时间倒序（最新在前）：缺失或空白发布时间的模型排在最后，发布时间相同再按名称
 * （不区分大小写）和稳定 ID 稳定排序。发布日期保留来源精度，这里只按字符串字典序比较，
 * 不解析或补全日期，因此 `2025` 早于 `2025-06`，`2025-06` 早于 `2025-06-01`。
 *
 * [priceRowsByCanonicalModelId] 是单独的报价投影，按规范模型 ID 分组；缺少该分组时表示暂无可用报价。
 */
fun List<ModelListRow>.toModelSummaries(
    priceRowsByCanonicalModelId: Map<String, List<ModelOfferingPriceRow>> = emptyMap(),
): List<ModelSummary> =
    groupBy { it.id }.values.mapNotNull { rows ->
        val model = rows.first()
        if (!LlmInclusionPolicy.includes(model.type, model.inputModalities, model.outputModalities)) {
            return@mapNotNull null
        }
        ModelSummary(
            id = model.id,
            name = model.name.takeIf { it.isNotBlank() } ?: model.id,
            releaseDate = model.releaseDate?.takeIf { it.isNotBlank() },
            contextLimitTokens = model.contextLimitTokens,
            outputLimitTokens = model.outputLimitTokens,
            inputModalities = model.inputModalities,
            outputModalities = model.outputModalities,
            openWeights = model.openWeights,
            license = model.license?.takeIf { it.isNotBlank() },
            price = priceRowsByCanonicalModelId[model.id].selectPriceFor(model.id),
        )
    }.sortedWith(
        compareByDescending<ModelSummary> { it.releaseDate?.takeIf(String::isNotBlank) }
            .thenBy { it.name.lowercase() }
            .thenBy { it.id },
    )

/**
 * 从同一规范模型的多个服务商报价中挑选一条用于列表展示。
 *
 * 规则：
 * 1. 只使用通过显式 `canonical_model_id` 关联到该规范模型的服务，不按名称或 ID 前缀猜测；
 * 2. 同一服务商有多个模型服务时，先取该服务商内输入价最低的一条（其次输出价最低，最后按服务内
 *    稳定键排序），避免把某个高速或预览变体当成该服务商的代表报价；
 * 3. 官方优先——[PriceProviderPolicy.officialProviderId] 认定的第一方服务商只要有可用基础报价就采用；
 * 4. 没有官方报价时，按第三方优先级取第一个有可用基础报价的服务商，未列入者排在最后，
 *    同优先级再按服务商 ID 稳定排序。
 *
 * 返回 null 表示没有可用基础报价，界面显示“暂无数据”，不补造零价或最低价。
 */
fun List<ModelOfferingPriceRow>?.selectPriceFor(modelId: String): ModelPriceSummary? {
    if (this.isNullOrEmpty()) return null
    val lab = modelId.substringBefore('/', missingDelimiterValue = "").takeIf { it.isNotBlank() }
    val byProvider = mapNotNull { row -> row.toCandidate() }
        .groupBy { it.providerId }
        .mapValues { (_, offers) -> offers.minWithOrNull(offerOrder)!! }
    if (byProvider.isEmpty()) return null
    val officialProviderId = PriceProviderPolicy.officialProviderId(lab)
    return byProvider[officialProviderId]?.toSummary(lab)
        ?: byProvider.values.minWithOrNull(providerOrder)!!.toSummary(lab)
}

/** 同一服务商内部按报价与稳定键排序，保证不同目录快照下结果一致。 */
private val offerOrder: Comparator<PriceOffer> =
    compareBy<PriceOffer> { it.pricing.input ?: Double.MAX_VALUE }
        .thenBy { it.pricing.output ?: Double.MAX_VALUE }
        .thenBy { it.offeringId }

/** 服务商之间先按第三方优先级，再按服务商 ID 稳定排序。 */
private val providerOrder: Comparator<PriceOffer> =
    compareBy<PriceOffer> { PriceProviderPolicy.thirdPartyRank(it.providerId) }
        .thenBy { it.providerId }

private class PriceOffer(
    val providerId: String,
    val providerName: String,
    val offeringId: String,
    val pricing: OfferingPricingLocal,
)

private fun PriceOffer.toSummary(lab: String?) = ModelPriceSummary(
    providerId = providerId,
    providerName = providerName,
    isOfficial = PriceProviderPolicy.officialProviderId(lab) == providerId,
    input = pricing.input,
    output = pricing.output,
    cacheRead = pricing.cacheRead,
    cacheWrite = pricing.cacheWrite,
    inputAudio = pricing.inputAudio,
    outputAudio = pricing.outputAudio,
    reasoning = pricing.reasoning,
    tiers = pricing.tiers.orEmpty().map { tier ->
        ModelPriceTierSummary(
            conditionType = tier.condition.type,
            conditionSizeTokens = tier.condition.size,
            input = tier.input,
            output = tier.output,
            cacheRead = tier.cacheRead,
            cacheWrite = tier.cacheWrite,
        )
    },
)

private fun ModelOfferingPriceRow.toCandidate(): PriceOffer? {
    val providerName = this.providerName?.takeIf { it.isNotBlank() } ?: return null
    if (!pricing.hasBaseUnitPrice()) return null
    return PriceOffer(providerId, providerName, offeringId, pricing)
}

/** 至少有输入或输出单价（或某个档位提供了其中之一）才算可用；明确的 0 视为可用报价。 */
private fun OfferingPricingLocal.hasBaseUnitPrice(): Boolean =
    input != null || output != null ||
        tiers?.any { it.input != null || it.output != null } == true
