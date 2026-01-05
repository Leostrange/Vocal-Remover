package com.vocalclear.app.di

import android.content.Context
import com.vocalclear.app.data.local.ArchiveManager
import com.vocalclear.app.data.local.LocalAudioDataSource
import com.vocalclear.app.data.local.OfflineAudioProcessor
import com.vocalclear.app.data.remote.RemoteAudioProcessor
import com.vocalclear.app.data.repository.*
import com.vocalclear.app.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(120, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(300, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.vocalclear.example.com/v1/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideVocalClearApi(retrofit: Retrofit): com.vocalclear.app.data.remote.VocalClearApi {
        return retrofit.create(com.vocalclear.app.data.remote.VocalClearApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLocalAudioDataSource(@ApplicationContext context: Context): LocalAudioDataSource {
        return LocalAudioDataSource(context)
    }

    @Provides
    @Singleton
    fun provideArchiveManager(): ArchiveManager {
        return ArchiveManager()
    }

    @Provides
    @Singleton
    fun provideOfflineAudioProcessor(): OfflineAudioProcessor {
        return OfflineAudioProcessor()
    }

    @Provides
    @Singleton
    fun provideRemoteAudioProcessor(api: com.vocalclear.app.data.remote.VocalClearApi): RemoteAudioProcessor {
        return RemoteAudioProcessor(api)
    }

    @Provides
    @Singleton
    fun provideVocalSectionDataSource(@ApplicationContext context: Context): com.vocalclear.app.data.datasource.VocalSectionDataSource {
        return com.vocalclear.app.data.datasource.VocalSectionDataSource(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAudioRepository(impl: AudioRepositoryImpl): AudioRepository

    @Binds
    @Singleton
    abstract fun bindOfflineProcessorRepository(impl: OfflineProcessorRepositoryImpl): OfflineProcessorRepository

    @Binds
    @Singleton
    abstract fun bindOnlineProcessorRepository(impl: OnlineProcessorRepositoryImpl): OnlineProcessorRepository

    @Binds
    @Singleton
    abstract fun bindArchiveRepository(impl: ArchiveRepositoryImpl): ArchiveRepository

    @Binds
    @Singleton
    abstract fun bindVocalSectionRepository(impl: VocalSectionRepositoryImpl): com.vocalclear.app.domain.repository.VocalSectionRepository
}
