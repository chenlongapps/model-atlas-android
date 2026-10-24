package io.github.chenlongapps.modelatlas.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.chenlongapps.modelatlas.data.local.CatalogDatabase
import io.github.chenlongapps.modelatlas.data.local.CatalogStore
import io.github.chenlongapps.modelatlas.data.local.RoomCatalogStore
import io.github.chenlongapps.modelatlas.data.remote.ModelsDevApi
import io.github.chenlongapps.modelatlas.data.remote.ModelsDevJson
import io.github.chenlongapps.modelatlas.data.repository.DefaultModelRepository
import io.github.chenlongapps.modelatlas.data.repository.ModelRepository
import io.github.chenlongapps.modelatlas.data.repository.FavoriteRepository
import io.github.chenlongapps.modelatlas.data.repository.RoomFavoriteRepository
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindings {
    @Binds
    abstract fun favorites(repository: RoomFavoriteRepository): FavoriteRepository

    @Binds
    abstract fun repository(repository: DefaultModelRepository): ModelRepository

    @Binds
    abstract fun store(store: RoomCatalogStore): CatalogStore
}

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    fun httpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    @Provides
    @Singleton
    fun api(client: OkHttpClient): ModelsDevApi = Retrofit.Builder()
        .baseUrl("https://models.dev/")
        .client(client)
        .addConverterFactory(ModelsDevJson.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ModelsDevApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): CatalogDatabase =
        Room.databaseBuilder<CatalogDatabase>(context, "catalog.db")
            .setDriver(AndroidSQLiteDriver())
            .build()

    @Provides
    fun catalogDao(database: CatalogDatabase) = database.catalogDao()

    @Provides
    fun favoriteDao(database: CatalogDatabase) = database.favoriteDao()
}
