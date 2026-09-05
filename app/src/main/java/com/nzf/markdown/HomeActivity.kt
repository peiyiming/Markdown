package com.nzf.markdown

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.EditText
import android.support.v7.widget.RecyclerView
import com.nzf.markdown.document.DocumentStore
import com.nzf.markdown.editor.MarkdownEditorActivity
import java.io.File

class HomeActivity : AppCompatActivity() {
    private lateinit var store: DocumentStore
    private lateinit var adapter: DocumentAdapter
    private lateinit var documentList: RecyclerView
    private var allDocuments: List<File> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        store = DocumentStore(this)
        adapter = DocumentAdapter({ open(it) }, { manage(it) })
        documentList = findViewById(R.id.rv_home_list)
        documentList.layoutManager = LinearLayoutManager(this)
        documentList.adapter = adapter
        findViewById<Button>(R.id.btn_new_document).setOnClickListener { createDocument() }
        findViewById<EditText>(R.id.et_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filter(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        allDocuments = store.list()
        adapter.submit(allDocuments)
    }

    private fun filter(query: String) {
        val q = query.trim().toLowerCase()
        adapter.submit(if (q.isEmpty()) allDocuments else allDocuments.filter {
            it.name.toLowerCase().contains(q)
        })
    }

    private fun createDocument() {
        val input = EditText(this)
        input.hint = "Document name"
        AlertDialog.Builder(this)
            .setTitle("New Markdown document")
            .setView(input)
            .setPositiveButton("Create") { _, _ -> open(store.create(input.text.toString())) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun manage(file: File) {
        AlertDialog.Builder(this)
            .setItems(arrayOf("Rename", "Delete")) { _, which ->
                if (which == 0) {
                    rename(file)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Delete document?")
                        .setMessage(file.name)
                        .setPositiveButton("Delete") { _, _ ->
                            store.delete(file)
                            refresh()
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            .show()
    }

    private fun rename(file: File) {
        val input = EditText(this)
        input.setText(file.nameWithoutExtension)
        AlertDialog.Builder(this)
            .setTitle("Rename document")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                store.rename(file, input.text.toString())
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun open(file: File) {
        startActivity(Intent(this, MarkdownEditorActivity::class.java)
            .putExtra(MarkdownEditorActivity.EXTRA_DOCUMENT_PATH, file.absolutePath))
    }
}
