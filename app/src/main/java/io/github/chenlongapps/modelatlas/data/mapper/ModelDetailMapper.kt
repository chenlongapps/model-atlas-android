package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.ModelDetailRecord
import io.github.chenlongapps.modelatlas.data.local.model.InterleavedLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.model.LlmInclusionPolicy
import io.github.chenlongapps.modelatlas.data.model.ModelDetail
import io.github.chenlongapps.modelatlas.data.model.ModelBenchmark
import io.github.chenlongapps.modelatlas.data.model.ModelDetailMetadata
import io.github.chenlongapps.modelatlas.data.model.ModelDetailOffering
import io.github.chenlongapps.modelatlas.data.model.ModelDetailPrice
import io.github.chenlongapps.modelatlas.data.model.ModelDetailPriceTier
import io.github.chenlongapps.modelatlas.data.model.ModelDetailProvider
import io.github.chenlongapps.modelatlas.data.model.ModelLink
import io.github.chenlongapps.modelatlas.data.model.ModelInterleaved
import io.github.chenlongapps.modelatlas.data.model.ModelReasoningOption
import io.github.chenlongapps.modelatlas.data.model.ModelWeight
import io.github.chenlongapps.modelatlas.data.model.PriceProviderPolicy

fun ModelDetailRecord.toModelDetail(): ModelDetail? {
    val m = model
    if (!LlmInclusionPolicy.includes(m.type, m.inputModalities, m.outputModalities)) return null
    val lab = m.id.substringBefore('/', missingDelimiterValue = "").takeIf(String::isNotBlank)
    val providers = offerings.filter { it.offering.canonicalModelId == m.id }
        .groupBy { it.provider.id }.map { (_, records) ->
            val provider = records.first().provider
            ModelDetailProvider(
                id = provider.id,
                name = provider.name.nonBlank() ?: provider.id,
                documentationUrl = provider.documentationUrl.nonBlank(),
                offerings = records.sortedBy { it.offering.id }.map { record ->
                    val o = record.offering
                    ModelDetailOffering(
                        id = o.id, name = o.name.nonBlank() ?: o.id,
                        metadata = ModelDetailMetadata(
                            description = o.description.nonBlank(),
                            family = o.family.nonBlank(),
                            knowledgeCutoff = o.knowledgeCutoff.nonBlank(),
                            releaseDate = o.releaseDate.nonBlank(),
                            sourceLastUpdated = o.sourceLastUpdated.nonBlank(),
                            attachment = o.attachment,
                            reasoning = o.reasoning,
                            toolCall = o.toolCall,
                            structuredOutput = o.structuredOutput,
                            temperature = o.temperature,
                            openWeights = o.openWeights,
                            contextLimitTokens = o.contextLimitTokens,
                            inputLimitTokens = o.inputLimitTokens,
                            outputLimitTokens = o.outputLimitTokens,
                            inputModalities = o.inputModalities,
                            outputModalities = o.outputModalities,
                        ),
                        status = o.status.nonBlank(), price = o.pricing?.toDetailPrice(),
                        modes = o.experimentalModes?.mapValues { (_, mode) -> mode.pricing?.toDetailPrice() },
                        reasoningOptions = o.reasoningOptions?.map {
                            ModelReasoningOption(it.type, it.min, it.max, it.values)
                        },
                        interleaved = when (val value = o.interleaved) {
                            is InterleavedLocal.Toggle -> ModelInterleaved(supported = value.enabled)
                            is InterleavedLocal.Field -> ModelInterleaved(field = value.field)
                            null -> null
                        },
                        source = o.source, fetchedAtEpochMillis = o.fetchedAtEpochMillis,
                    )
                },
            )
        }.sortedWith(
            compareBy<ModelDetailProvider> { it.id != PriceProviderPolicy.officialProviderId(lab) }
                .thenBy { PriceProviderPolicy.thirdPartyRank(it.id) }.thenBy { it.id },
        )
    return ModelDetail(
        id = m.id, name = m.name.nonBlank() ?: m.id,
        metadata = ModelDetailMetadata(
            description = m.description.nonBlank(),
            family = m.family.nonBlank(),
            knowledgeCutoff = m.knowledgeCutoff.nonBlank(),
            releaseDate = m.releaseDate.nonBlank(),
            sourceLastUpdated = m.sourceLastUpdated.nonBlank(),
            attachment = m.attachment,
            reasoning = m.reasoning,
            toolCall = m.toolCall,
            structuredOutput = m.structuredOutput,
            temperature = m.temperature,
            openWeights = m.openWeights,
            contextLimitTokens = m.contextLimitTokens,
            inputLimitTokens = m.inputLimitTokens,
            outputLimitTokens = m.outputLimitTokens,
            inputModalities = m.inputModalities,
            outputModalities = m.outputModalities,
        ),
        license = m.license.nonBlank(),
        weights = m.weights?.map { ModelWeight(it.url, it.label, it.quantization, it.format) },
        links = m.links?.map { ModelLink(it.url, it.label, it.type) },
        benchmarks = m.benchmarks?.map {
            ModelBenchmark(it.name, it.score, it.metric, it.source, it.date, it.version, it.variant, it.dataset, it.harness)
        },
        source = m.source, fetchedAtEpochMillis = m.fetchedAtEpochMillis, providers = providers,
    )
}

private fun String?.nonBlank() = this?.takeIf(String::isNotBlank)

private fun OfferingPricingLocal.toDetailPrice() = ModelDetailPrice(
    input = input,
    output = output,
    cacheRead = cacheRead,
    cacheWrite = cacheWrite,
    reasoning = reasoning,
    inputAudio = inputAudio,
    outputAudio = outputAudio,
    tiers = tiers?.map {
        ModelDetailPriceTier(
            it.condition.type, it.condition.size,
            ModelDetailPrice(input = it.input, output = it.output, cacheRead = it.cacheRead, cacheWrite = it.cacheWrite,
                reasoning = it.reasoning, inputAudio = it.inputAudio, outputAudio = it.outputAudio),
        )
    },
)
