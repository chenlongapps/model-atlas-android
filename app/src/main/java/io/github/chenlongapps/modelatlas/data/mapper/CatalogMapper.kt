package io.github.chenlongapps.modelatlas.data.mapper

import io.github.chenlongapps.modelatlas.data.local.CatalogSnapshot
import io.github.chenlongapps.modelatlas.data.local.entity.ModelEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ModelOfferingEntity
import io.github.chenlongapps.modelatlas.data.local.entity.ProviderEntity
import io.github.chenlongapps.modelatlas.data.local.model.ExperimentalModeLocal
import io.github.chenlongapps.modelatlas.data.local.model.InterleavedLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelLinkLocal
import io.github.chenlongapps.modelatlas.data.local.model.ModelWeightLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPriceTierLocal
import io.github.chenlongapps.modelatlas.data.local.model.OfferingPricingLocal
import io.github.chenlongapps.modelatlas.data.local.model.PriceTierConditionLocal
import io.github.chenlongapps.modelatlas.data.local.model.ProviderOverrideLocal
import io.github.chenlongapps.modelatlas.data.local.model.ReasoningOptionLocal
import io.github.chenlongapps.modelatlas.data.remote.dto.CostDto
import io.github.chenlongapps.modelatlas.data.remote.dto.InterleavedDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ModelOfferingDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ProviderDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ProviderOverrideDto
import javax.inject.Inject

/** 存储保留整个目录；LLM 收录由独立业务规则决定，不破坏未关联的服务与报价。 */
class CatalogMapper @Inject constructor() {
    fun map(
        models: Map<String, ModelDto>,
        providers: Map<String, ProviderDto>,
        modelsFetchedAt: Long,
        providersFetchedAt: Long,
    ) = CatalogSnapshot(
        models = models.map { (key, model) -> model.toEntity(key, modelsFetchedAt) },
        providers = providers.map { (key, provider) ->
            ProviderEntity(
                id = key,
                remoteId = provider.id,
                name = provider.name,
                fetchedAtEpochMillis = providersFetchedAt,
                apiUrl = provider.api,
                documentationUrl = provider.doc,
                npmPackage = provider.npm,
                environmentVariables = provider.env,
            )
        },
        offerings = providers.flatMap { (providerKey, provider) ->
            provider.models.map { (key, offering) ->
                offering.toEntity(providerKey, key, providersFetchedAt)
            }
        },
    )
}

private fun ModelDto.toEntity(key: String, fetchedAt: Long) = ModelEntity(
    id = key,
    remoteId = id,
    name = name,
    fetchedAtEpochMillis = fetchedAt,
    description = description,
    family = family,
    type = type,
    knowledgeCutoff = knowledge,
    releaseDate = releaseDate,
    sourceLastUpdated = lastUpdated,
    attachment = attachment,
    reasoning = reasoning,
    toolCall = toolCall,
    structuredOutput = structuredOutput,
    temperature = temperature,
    openWeights = openWeights,
    contextLimitTokens = limit?.context,
    inputLimitTokens = limit?.input,
    outputLimitTokens = limit?.output,
    inputModalities = modalities?.input,
    outputModalities = modalities?.output,
    license = license,
    weights = weights?.map { ModelWeightLocal(it.url, it.label, it.quantization, it.format) },
    links = links?.map { ModelLinkLocal(it.url, it.label, it.type) },
)

private fun ModelOfferingDto.toEntity(providerKey: String, key: String, fetchedAt: Long) =
    ModelOfferingEntity(
        providerId = providerKey,
        id = key,
        remoteId = id,
        name = name,
        fetchedAtEpochMillis = fetchedAt,
        canonicalModelId = canonicalModelId,
        description = description,
        family = family,
        type = type,
        knowledgeCutoff = knowledge,
        releaseDate = releaseDate,
        sourceLastUpdated = lastUpdated,
        attachment = attachment,
        reasoning = reasoning,
        toolCall = toolCall,
        structuredOutput = structuredOutput,
        temperature = temperature,
        openWeights = openWeights,
        contextLimitTokens = limit?.context,
        inputLimitTokens = limit?.input,
        outputLimitTokens = limit?.output,
        inputModalities = modalities?.input,
        outputModalities = modalities?.output,
        status = status,
        reasoningOptions = reasoningOptions?.map { ReasoningOptionLocal(it.type, it.min, it.max, it.values) },
        interleaved = when (val value = interleaved) {
            is InterleavedDto.Toggle -> InterleavedLocal.Toggle(value.enabled)
            is InterleavedDto.Field -> InterleavedLocal.Field(value.field)
            null -> null
        },
        pricing = cost?.toLocal(),
        providerOverride = provider?.toLocal(),
        experimentalModes = experimental?.modes?.mapValues { (_, mode) ->
            ExperimentalModeLocal(mode.cost?.toLocal(), mode.provider?.toLocal())
        },
    )

private fun CostDto.toLocal(): OfferingPricingLocal = OfferingPricingLocal(
    input = input,
    output = output,
    cacheRead = cacheRead,
    cacheWrite = cacheWrite,
    inputAudio = inputAudio,
    outputAudio = outputAudio,
    reasoning = reasoning,
    tiers = tiers?.map {
        OfferingPriceTierLocal(
            condition = PriceTierConditionLocal(it.tier.type, it.tier.size),
            input = it.input,
            output = it.output,
            cacheRead = it.cacheRead,
            cacheWrite = it.cacheWrite,
            inputAudio = it.inputAudio,
            outputAudio = it.outputAudio,
            reasoning = it.reasoning,
        )
    },
    contextOver200k = contextOver200k?.toLocal(),
)

private fun ProviderOverrideDto.toLocal() = ProviderOverrideLocal(
    npmPackage = npm,
    apiUrl = api,
    shape = shape,
    body = body,
    headers = headers,
)
