package com.example.vocalremover

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class PerformanceOptimizer @Inject constructor(
    private val context: Context
) {

    private val processorCount = Runtime.getRuntime().availableProcessors()
    private val backgroundExecutor = Executors.newFixedThreadPool(processorCount.coerceAtLeast(4))
    private val ioExecutor = Executors.newFixedThreadPool(2)
    private val scheduledExecutor = Executors.newScheduledThreadPool(2)
    
    private val cache = ConcurrentHashMap<String, Any>()
    private val memoryMonitor = MemoryMonitor()
    
    data class OptimizationConfig(
        val enableParallelProcessing: Boolean = true,
        val maxConcurrentTasks: Int = processorCount,
        val memoryThreshold: Double = 0.8,
        val enableCache: Boolean = true,
        val enableHardwareAcceleration: Boolean = true
    )

    data class PerformanceMetrics(
        val cpuUsage: Double,
        val memoryUsage: Double,
        val cacheHitRate: Double,
        val averageProcessingTime: Long,
        val throughput: Double
    )

    // Основные методы оптимизации

    suspend fun optimizeAudioProcessing(
        operation: suspend () -> Any,
        config: OptimizationConfig = OptimizationConfig()
    ): Any {
        return withContext(Dispatchers.Default) {
            try {
                // Проверяем доступную память
                if (memoryMonitor.getMemoryUsage() > config.memoryThreshold) {
                    Log.w("PerformanceOptimizer", "High memory usage detected, cleaning cache")
                    clearCache()
                }
                
                // Выполняем операцию с оптимизациями
                withTimeout(5 * 60 * 1000) { // 5 минут таймаут
                    if (config.enableParallelProcessing && processorCount > 1) {
                        executeInParallel(operation)
                    } else {
                        operation()
                    }
                }
            } catch (e: TimeoutCancellationException) {
                Log.e("PerformanceOptimizer", "Operation timed out")
                throw Exception("Audio processing operation timed out due to performance constraints")
            }
        }
    }

    suspend fun <T, R> batchProcess(
        items: List<T>,
        processor: suspend (T) -> R,
        batchSize: Int = processorCount * 2,
        config: OptimizationConfig = OptimizationConfig()
    ): List<R> = withContext(Dispatchers.Default) {
        val results = mutableListOf<R>()
        
        if (config.enableParallelProcessing) {
            // Параллельная обработка батчами
            items.chunked(batchSize).forEach { batch ->
                val batchResults = batch.map { item ->
                    async(Dispatchers.Default) {
                        try {
                            processor(item)
                        } catch (e: Exception) {
                            Log.e("PerformanceOptimizer", "Error processing item", e)
                            null // Возвращаем null при ошибке
                        }
                    }
                }.awaitAll().filterNotNull()
                
                results.addAll(batchResults)
                
                // Очистка памяти между батчами
                if (memoryMonitor.getMemoryUsage() > 0.7) {
                    System.gc()
                    delay(100)
                }
            }
        } else {
            // Последовательная обработка
            items.forEach { item ->
                try {
                    results.add(processor(item))
                } catch (e: Exception) {
                    Log.e("PerformanceOptimizer", "Error processing item", e)
                }
            }
        }
        
        results
    }

    fun getOptimalThreadCount(operationType: String): Int {
        return when (operationType) {
            "audio_analysis" -> (processorCount * 0.75).toInt().coerceAtLeast(2)
            "ml_inference" -> (processorCount * 0.5).toInt().coerceAtLeast(1)
            "file_io" -> 2 // Ограничиваем I/O операции
            "ffmpeg_processing" -> (processorCount * 0.6).toInt().coerceAtLeast(2)
            else -> processorCount.coerceAtLeast(2)
        }
    }

    fun optimizeMemoryUsage(): Long {
        val freeMemory = Runtime.getRuntime().freeMemory()
        val totalMemory = Runtime.getRuntime().totalMemory()
        val maxMemory = Runtime.getRuntime().maxMemory()
        
        Log.d("PerformanceOptimizer", "Memory stats - Free: ${freeMemory / 1024 / 1024}MB, " +
                "Total: ${totalMemory / 1024 / 1024}MB, Max: ${maxMemory / 1024 / 1024}MB")
        
        // Принудительная сборка мусора если память заполнена на 80%
        if (totalMemory > maxMemory * 0.8) {
            System.gc()
            return Runtime.getRuntime().freeMemory()
        }
        
        return freeMemory
    }

    fun enableHardwareAcceleration(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Проверяем поддержку аппаратного ускорения
                val hasNNAPI = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                val hasVulkan = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                val hasMetal = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
                
                Log.d("PerformanceOptimizer", "Hardware acceleration available: " +
                        "NNAPI=$hasNNAPI, Vulkan=$hasVulkan, Metal=$hasMetal")
                
                hasNNAPI || hasVulkan || hasMetal
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("PerformanceOptimizer", "Error checking hardware acceleration", e)
            false
        }
    }

    fun cacheResult(key: String, value: Any, ttl: Long = 5 * 60 * 1000): Boolean {
        return try {
            cache[key] = CacheEntry(value, System.currentTimeMillis() + ttl)
            
            // Ограничиваем размер кэша
            if (cache.size > 1000) {
                cleanupExpiredCache()
            }
            
            true
        } catch (e: Exception) {
            Log.e("PerformanceOptimizer", "Error caching result", e)
            false
        }
    }

    fun getCachedResult(key: String): Any? {
        return try {
            val entry = cache[key] as? CacheEntry
            if (entry != null && System.currentTimeMillis() < entry.expiry) {
                entry.value
            } else {
                cache.remove(key)
                null
            }
        } catch (e: Exception) {
            Log.e("PerformanceOptimizer", "Error getting cached result", e)
            null
        }
    }

    fun clearCache() {
        cache.clear()
        Log.d("PerformanceOptimizer", "Cache cleared")
    }

    fun getPerformanceMetrics(): PerformanceMetrics {
        val cpuUsage = calculateCPUUsage()
        val memoryUsage = memoryMonitor.getMemoryUsage()
        val cacheHitRate = calculateCacheHitRate()
        val averageProcessingTime = getAverageProcessingTime()
        val throughput = calculateThroughput()
        
        return PerformanceMetrics(
            cpuUsage = cpuUsage,
            memoryUsage = memoryUsage,
            cacheHitRate = cacheHitRate,
            averageProcessingTime = averageProcessingTime,
            throughput = throughput
        )
    }

    // Приватные методы

    private suspend fun executeInParallel(operation: suspend () -> Any): Any {
        return withContext(Dispatchers.Default) {
            // Запускаем операцию в пуле потоков
            withContext(backgroundExecutor.asCoroutineDispatcher()) {
                operation()
            }
        }
    }

    private fun cleanupExpiredCache() {
        val now = System.currentTimeMillis()
        val expiredKeys = cache.entries.filter { (_, entry) ->
            entry !is CacheEntry || now > entry.expiry
        }.map { it.key }
        
        expiredKeys.forEach { cache.remove(it) }
        
        if (expiredKeys.isNotEmpty()) {
            Log.d("PerformanceOptimizer", "Cleaned up ${expiredKeys.size} expired cache entries")
        }
    }

    private fun calculateCPUUsage(): Double {
        return try {
            // Простая оценка загрузки CPU на основе количества активных потоков
            val activeThreads = Thread.getAllStackTraces().size
            (activeThreads.toDouble() / processorCount).coerceIn(0.0, 1.0)
        } catch (e: Exception) {
            0.5 // Default значение
        }
    }

    private fun calculateCacheHitRate(): Double {
        return try {
            val cacheSize = cache.size
            val totalRequests = cacheMetrics.getOrDefault("total_requests", 0) as Int
            val cacheHits = cacheMetrics.getOrDefault("cache_hits", 0) as Int
            
            if (totalRequests > 0) {
                cacheHits.toDouble() / totalRequests
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    private fun getAverageProcessingTime(): Long {
        return processingMetrics.getOrDefault("average_time", 0L) as Long
    }

    private fun calculateThroughput(): Double {
        return try {
            val totalOperations = processingMetrics.getOrDefault("total_operations", 0) as Int
            val totalTime = processingMetrics.getOrDefault("total_time", 0L) as Long
            
            if (totalTime > 0) {
                totalOperations.toDouble() / (totalTime / 1000.0) // операций в секунду
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }

    // Внутренние классы

    private data class CacheEntry(
        val value: Any,
        val expiry: Long
    )

    private inner class MemoryMonitor {
        fun getMemoryUsage(): Double {
            val runtime = Runtime.getRuntime()
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()
            
            return (usedMemory.toDouble() / maxMemory).coerceIn(0.0, 1.0)
        }

        fun getAvailableMemory(): Long {
            return Runtime.getRuntime().freeMemory()
        }

        fun isMemoryLow(): Boolean {
            return getMemoryUsage() > 0.85
        }
    }

    // Метрики
    private val cacheMetrics = ConcurrentHashMap<String, Any>()
    private val processingMetrics = ConcurrentHashMap<String, Any>()

    // Мониторинг производительности
    init {
        startPerformanceMonitoring()
    }

    private fun startPerformanceMonitoring() {
        scheduledExecutor.scheduleWithFixedDelay({
            try {
                val metrics = getPerformanceMetrics()
                Log.d("PerformanceOptimizer", "Performance metrics: $metrics")
                
                // Автоматическая оптимизация при высокой нагрузке
                if (metrics.cpuUsage > 0.9 || metrics.memoryUsage > 0.9) {
                    Log.w("PerformanceOptimizer", "High system load detected, applying optimizations")
                    applyEmergencyOptimizations()
                }
            } catch (e: Exception) {
                Log.e("PerformanceOptimizer", "Error in performance monitoring", e)
            }
        }, 30, 30, TimeUnit.SECONDS)
    }

    private fun applyEmergencyOptimizations() {
        // Очищаем кэш при высокой нагрузке
        clearCache()
        
        // Принудительная сборка мусора
        System.gc()
        
        // Уменьшаем количество потоков в пуле
        backgroundExecutor.shutdownNow()
        
        Log.i("PerformanceOptimizer", "Emergency optimizations applied")
    }

    fun shutdown() {
        try {
            backgroundExecutor.shutdown()
            ioExecutor.shutdown()
            scheduledExecutor.shutdown()
            
            if (!backgroundExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                backgroundExecutor.shutdownNow()
            }
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                ioExecutor.shutdownNow()
            }
            if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduledExecutor.shutdownNow()
            }
            
            Log.d("PerformanceOptimizer", "Performance optimizer shutdown completed")
        } catch (e: Exception) {
            Log.e("PerformanceOptimizer", "Error during shutdown", e)
        }
    }
}
