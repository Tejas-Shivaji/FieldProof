package com.fieldproof.app.data.local

import android.content.Context
import java.io.File
import java.io.InputStream

class EvidenceFileStore(context: Context) {
    private val root: File = File(context.filesDir, "evidence").apply { mkdirs() }
    private val reports: File = File(context.filesDir, "reports").apply { mkdirs() }
    private val captureCache: File = File(context.cacheDir, "capture").apply { mkdirs() }

    fun captureTempFile(name: String): File = File(captureCache, name)

    fun persist(observationId: String, evidenceId: String, source: InputStream, extension: String): File {
        val dir = File(root, observationId).apply { mkdirs() }
        val dest = File(dir, "$evidenceId.$extension")
        source.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest
    }

    fun fileFor(relativePath: String): File = File(root, relativePath)

    fun relativePath(observationId: String, file: File): String {
        val prefix = root.absolutePath.trimEnd(File.separatorChar) + File.separator
        val absolute = file.absolutePath
        require(absolute.startsWith(prefix)) { "evidence file outside store" }
        return absolute.removePrefix(prefix).replace(File.separatorChar, '/')
    }

    fun writeReport(name: String, text: String): File {
        val file = File(reports, name)
        file.writeText(text)
        return file
    }
}
