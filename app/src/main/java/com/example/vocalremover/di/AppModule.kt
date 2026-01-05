package com.example.vocalremover.di

import android.content.Context
import com.example.vocalremover.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt модуль для dependency injection
 * Предоставляет все основные компоненты приложения
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context {
        return context
    }

    @Provides
    @Singleton
    fun provideAudioAnalyzer(context: Context): AudioAnalyzer {
        return AudioAnalyzer(context)
    }

    @Provides
    @Singleton
    fun provideMLModelManager(context: Context): MLModelManager {
        return MLModelManager(context)
    }

    @Provides
    @Singleton
    fun providePerformanceOptimizer(context: Context): PerformanceOptimizer {
        return PerformanceOptimizer(context)
    }

    @Provides
    @Singleton
    fun provideVocalProcessor(
        context: Context
    ): VocalProcessor {
        return VocalProcessor(context)
    }

    @Provides
    fun provideFilterChain(): FilterChain {
        return FilterChain()
    }

    @Provides
    fun provideFilterPresets(): FilterPresets {
        return FilterPresets
    }

    @Provides
    fun provideFilterUtils(): FilterUtils {
        return FilterUtils
    }
}
