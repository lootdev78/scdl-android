package io.github.lootdev.scdl.common.utils

import android.system.Os
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.io.IOUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

object ZipUtils {
    fun unzip(sourceFile: File?, targetDirectory: File) {
        val source = requireNotNull(sourceFile) { "sourceFile must not be null" }
        ZipFile(source).use { zipFile ->
            val entries = zipFile.entries
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryDestination = File(targetDirectory, entry.name)
                // prevent zipSlip
                if (!entryDestination.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                    throw IllegalAccessException("Entry is outside of the target dir: " + entry.name)
                }
                if (entry.isDirectory) {
                    entryDestination.mkdirs()
                } else if (entry.isUnixSymlink) {
                    zipFile.getInputStream(entry).use { `in` ->
                        val symlink = IOUtils.toString(`in`, StandardCharsets.UTF_8)
                        Os.symlink(symlink, entryDestination.absolutePath)
                    }
                } else {
                    entryDestination.parentFile?.mkdirs()
                    zipFile.getInputStream(entry).use { `in` ->
                        FileOutputStream(entryDestination).use { out ->
                            IOUtils.copy(
                                `in`,
                                out
                            )
                        }
                    }
                }
            }
        }
    }

    fun unzip(inputStream: InputStream?, targetDirectory: File) {
        val source = requireNotNull(inputStream) { "inputStream must not be null" }
        ZipArchiveInputStream(BufferedInputStream(source)).use { zis ->
            var entry: ZipArchiveEntry?
            while (zis.nextZipEntry.also { entry = it } != null) {
                val currentEntry = requireNotNull(entry)
                val entryDestination = File(targetDirectory, currentEntry.name)
                // prevent zipSlip
                if (!entryDestination.canonicalPath.startsWith(targetDirectory.canonicalPath + File.separator)) {
                    throw IllegalAccessException("Entry is outside of the target dir: " + currentEntry.name)
                }
                if (currentEntry.isDirectory) {
                    entryDestination.mkdirs()
                } else {
                    entryDestination.parentFile?.mkdirs()
                    FileOutputStream(entryDestination).use { out -> IOUtils.copy(zis, out) }
                }
            }
        }
    }
}