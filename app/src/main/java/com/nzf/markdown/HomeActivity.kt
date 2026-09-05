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
import com.nzf.markdown.document.DocumentStore
import com.nzf.markdown.editor.MarkdownEditorActivity
import java.io.File

class HomeActivity : AppCompatActivity() {
    private lateinit var store: DocumentStore
    private lateinit var adapter: DocumentAdapter
    private lateinit var allDocuments: List<File>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        store = DocumentStore(this)
        adapter = DocumentAdapter { open(it) }
        rv_home_list.layoutManager = LinearLayoutManager(this)
        rv_home_list.adapter = adapter
        findViewById<Button>(R.id.btn_new_document).setOnClickListener { createDocument() }
        findViewById<EditText>(R.id.et_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter(s?.toString().orEmpty()) }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() { allDocuments = store.list(); adapter.submit(allDocuments) }
    private fun filter(query: String) {
        val q = query.trim().toLowerCase()
        adapter.submit(if (q.isEmpty()) allDocuments else allDocuments.filter { it.name.toLowerCase().contains(q) })
    }

    private fun createDocument() {
        val input = EditText(this); input.hint = "Document name"
        AlertDialog.Builder(this).setTitle("New Markdown document").setView(input)
            .setPositiveButton("Create") { _, _ -> open(store.create(input.text.toString())) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun open(file: File) {
        startActivity(Intent(this, MarkdownEditorActivity::class.java).putExtra(MarkdownEditorActivity.EXTRA_DOCUMENT_PATH, file.absolutePath))
    }
}
