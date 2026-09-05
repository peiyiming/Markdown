package com.nzf.markdown.document

import android.content.Context
import java.io.File

/** Simple local document repository for the MVP. */
class DocumentStore(private val context: Context) {
    private val root: File
        get() = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }

    fun list(): List<File> = root.listFiles { file -> file.isFile && file.extension.equals("md", true) }
        ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun create(name: String): File {
        val safe = name.trim().ifEmpty { "Untitled" }.replace(Regex("[\\/:*?\"<>|]"), "_")
        var file = File(root, if (safe.endsWith(".md", true)) safe else "$safe.md")
        var index = 1
        while (file.exists()) {
            file = File(root, "${safe.removeSuffix(".md")} ($index).md")
            index++
        }
        file.createNewFile()
        return file
    }

    fun rename(file: File, name: String): File? {
        val safe = name.trim().ifEmpty { return null }.replace(Regex("[\\/:*?\"<>|]"), "_")
        val target = File(root, if (safe.endsWith(".md", true)) safe else "$safe.md")
        if (target.exists() && target.absolutePath != file.absolutePath) return null
        return if (file.renameTo(target)) target else null
    }

    fun delete(file: File): Boolean = file.exists() && file.delete()
    fun save(file: File, content: String) = file.writeText(content)
    fun read(file: File): String = if (file.exists()) file.readText() else ""
}
