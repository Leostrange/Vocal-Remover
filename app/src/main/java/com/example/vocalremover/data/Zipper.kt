package com.example.vocalremover.data

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Zipper {
    fun zipFiles(files: List<File>, outputFile: File) {
        ZipOutputStream(FileOutputStream(outputFile)).use { zipOut ->
            files.forEach { file ->
                val fis = FileInputStream(file)
                val zipEntry = ZipEntry(file.name)
                zipOut.putNextEntry(zipEntry)
                fis.copyTo(zipOut)
                fis.close()
            }
        }
    }
}
