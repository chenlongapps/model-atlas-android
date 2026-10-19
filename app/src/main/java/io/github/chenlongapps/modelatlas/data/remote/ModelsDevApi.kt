package io.github.chenlongapps.modelatlas.data.remote

import io.github.chenlongapps.modelatlas.data.remote.dto.ModelDto
import io.github.chenlongapps.modelatlas.data.remote.dto.ProviderDto
import retrofit2.http.GET

interface ModelsDevApi {
    @GET("models.json?type=all")
    suspend fun getModels(): Map<String, ModelDto>

    @GET("api.json?type=all")
    suspend fun getProviders(): Map<String, ProviderDto>
}
