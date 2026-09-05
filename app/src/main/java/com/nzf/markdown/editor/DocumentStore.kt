package com.nzf.markdown.editor

import android.content.Context
import java.io.File

/**
 * Lightweight local document store for the first commercial MVP.
 *
 * The store keeps documents inside the application's external files area so
 * they remain isolated from arbitrary storage permissions while the product
 * architecture is being stabilised.
 */
class DocumentStore(context: Context) {

    private val documentsDir: File = File(context.getExternalFilesDir(null), "documents")

    init {
        if (!documentsDir.exists()) {
            documentsDir.mkdirs()
        }
    }

    fun create(title: String): File {
        val safeTitle = title
            .trim()
            .ifBlank { "Untitled" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "-")
        var file = File(documentsDir, "$safeTitle.md")
        var index = 2
        while (file.exists()) {
            file = File(documentsDir, "$safeTitle-$index.md")
            index++
        }
        file.createNewFile()
        return file
    }

    fun save(path: String, content: String) {
        File(path).writeText(content)
    }

    fun load(path: String): String = File(path).takeIf { it.exists() }?.readText().orEmpty()

    fun list(): List<File> = documentsDir.listFiles()
        ?.filter { it.isFile && it.extension.equals("md", true) }
        ?.sortedByDescending { it.lastModified() }
        .orEmpty()
}
