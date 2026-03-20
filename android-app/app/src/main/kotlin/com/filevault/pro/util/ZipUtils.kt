package com.filevault.pro.util

import java.io.File
import java.util.zip.ZipFile

object ZipUtils {
    fun unzip(zipFile: File, destDir: File): Boolean {
        return try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val outFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
