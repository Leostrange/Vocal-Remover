package com.example.vocalremover.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class OutputWriter(private val context: Context) {
    suspend fun saveToCache(file: File, name: String): File {
        return withContext(Dispatchers.IO) {
            val dest = File(context.cacheDir, name)
            FileInputStream(file).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
            dest
        }
    }
}
