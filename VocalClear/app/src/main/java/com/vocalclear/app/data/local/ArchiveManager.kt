package com.vocalclear.app.data.local

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Archive manager for creating ZIP files
 */
@Singleton
class ArchiveManager @Inject constructor() {

    companion object {
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Create a ZIP archive from files
     */
    suspend fun createArchive(
        files: List<File>,
        archiveName: String,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val archiveFile = File.createTempFile("archive_", ".zip")

            ZipOutputStream(BufferedOutputStream(FileOutputStream(archiveFile))).use { zos ->
                files.forEachIndexed { index, file ->
                    if (file.exists()) {
                        onProgress(
                            (index * 100 / files.size.coerceAtLeast(1)),
                            "Adding ${file.name}..."
                        )

                        addFileToZip(zos, file, file.name)
                    }
                }

                // Add manifest/info file
                addManifestFile(zos, files)
            }

            onProgress(100, "Archive created!")

            // Rename to proper name
            val finalFile = File(archiveFile.parent, archiveName)
            archiveFile.renameTo(finalFile)

            Result.success(finalFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun addFileToZip(zos: ZipOutputStream, file: File, entryName: String) {
        BufferedInputStream(FileInputStream(file)).use { bis ->
            val entry = ZipEntry(entryName)
            zos.putNextEntry(entry)

            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            while (bis.read(buffer).also { bytesRead = it } != -1) {
                zos.write(buffer, 0, bytesRead)
            }
            zos.closeEntry()
        }
    }

    private fun addManifestFile(zos: ZipOutputStream, files: List<File>) {
        val manifestContent = buildString {
            appendLine("VocalClear Processing Result")
            appendLine("===========================")
            appendLine()
            appendLine("Generated: ${java.util.Date()}")
            appendLine()
            appendLine("Files:")
            files.forEach { file ->
                appendLine("- ${file.name} (${file.length() / 1024} KB)")
            }
            appendLine()
            appendLine("Processing Mode: OFFLINE")
            appendLine("Algorithm: Center Channel Cancellation")
        }

        val entry = ZipEntry("manifest.txt")
        zos.putNextEntry(entry)
        zos.write(manifestContent.toByteArray())
        zos.closeEntry()
    }

    /**
     * Extract a ZIP archive
     */
    suspend fun extractArchive(
        archiveFile: File,
        outputDir: File,
        onProgress: (Int, String) -> Unit = { _, _ -> }
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            outputDir.mkdirs()
            val extractedFiles = mutableListOf<File>()

            java.util.zip.ZipFile(archiveFile).use { zipFile ->
                val entries = zipFile.entries().toList()
                entries.forEachIndexed { index, entry ->
                    if (!entry.isDirectory) {
                        onProgress(
                            (index * 100 / entries.size.coerceAtLeast(1)),
                            "Extracting ${entry.name}..."
                        )

                        val file = File(outputDir, entry.name)
                        file.parentFile?.mkdirs()

                        zipFile.getInputStream(entry).use { input ->
                            BufferedInputStream(input).use { bis ->
                                FileOutputStream(file).use { fos ->
                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var bytesRead: Int
                                    while (bis.read(buffer).also { bytesRead = it } != -1) {
                                        fos.write(buffer, 0, bytesRead)
                                    }
                                }
                            }
                        }

                        extractedFiles.add(file)
                    }
                }
            }

            onProgress(100, "Extraction complete!")
            Result.success(extractedFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
