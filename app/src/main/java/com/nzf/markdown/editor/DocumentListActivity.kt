package com.nzf.markdown.editor

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.nzf.markdown.R
import com.nzf.markdown.document.DocumentStore
import java.io.File

/** First functional workspace for the Markdown product MVP. */
class DocumentListActivity : AppCompatActivity() {

    private lateinit var store: DocumentStore
    private lateinit var documentContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = DocumentStore(this)
        setContentView(R.layout.activity_document_list)
        documentContainer = findViewById(R.id.document_container)
        findViewById<Button>(R.id.btn_new_document).setOnClickListener { createDocument() }
    }

    override fun onResume() {
        super.onResume()
        renderDocuments()
    }

    private fun createDocument() {
        val file = store.create("Untitled")
        openEditor(file)
    }

    private fun renderDocuments() {
        documentContainer.removeAllViews()
        val documents = store.list()
        if (documents.isEmpty()) {
            val empty = TextView(this).apply {
                text = "No documents yet\nCreate your first Markdown document."
                gravity = Gravity.CENTER
                textSize = 16f
                setPadding(24, 64, 24, 64)
            }
            documentContainer.addView(empty)
            return
        }
        documents.forEach { file -> documentContainer.addView(documentView(file)) }
    }

    private fun documentView(file: File): View {
        return TextView(this).apply {
            text = "📄 ${file.nameWithoutExtension}\nUpdated: ${java.text.DateFormat.getDateTimeInstance().format(file.lastModified())}"
            textSize = 16f
            setPadding(32, 28, 32, 28)
            setOnClickListener { openEditor(file) }
        }
    }

    private fun openEditor(file: File) {
        startActivity(Intent(this, MarkdownEditorActivity::class.java).apply {
            putExtra(MarkdownEditorActivity.EXTRA_DOCUMENT_PATH, file.absolutePath)
        })
    }
}
