package com.example.vocalremover

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Основной класс приложения для инициализации Hilt
 */
@HiltAndroidApp
class VocalRemoverApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Инициализация приложения
        instance = this
    }

    companion object {
        lateinit var instance: VocalRemoverApp
            private set
    }
}
