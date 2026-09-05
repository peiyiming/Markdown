package com.nzf.markdown

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import com.nzf.markdown.document.DocumentStore
import com.nzf.markdown.editor.MarkdownEditorActivity
import java.io.File

class HomeActivity : AppCompatActivity() {
    private lateinit var store: DocumentStore
    private lateinit var adapter: DocumentAdapter
    private lateinit var documentList: RecyclerView
    private lateinit var emptyState: LinearLayout
    private var allDocuments: List<File> = emptyList()
    private var currentQuery: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        store = DocumentStore(this)
        adapter = DocumentAdapter({ open(it) }, { manage(it) })
        documentList = findViewById(R.id.rv_home_list)
        emptyState = findViewById(R.id.layout_empty_state)
        documentList.layoutManager = LinearLayoutManager(this)
        documentList.adapter = adapter
        findViewById<Button>(R.id.btn_new_document).setOnClickListener { createDocument() }
        findViewById<Button>(R.id.btn_empty_create).setOnClickListener { createDocument() }
        findViewById<EditText>(R.id.et_search).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString().orEmpty()
                filter(currentQuery)
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        allDocuments = store.list().sortedByDescending { it.lastModified() }
        filter(currentQuery)
    }

    private fun filter(query: String) {
        val q = query.trim().toLowerCase()
        val visibleDocuments = if (q.isEmpty()) allDocuments else allDocuments.filter {
            it.name.toLowerCase().contains(q)
        }
        adapter.submit(visibleDocuments)
        documentList.visibility = if (visibleDocuments.isEmpty()) View.GONE else View.VISIBLE
        emptyState.visibility = if (allDocuments.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun createDocument() {
        val input = EditText(this)
        input.hint = "文档名称"
        AlertDialog.Builder(this)
            .setTitle("新建 Markdown 文档")
            .setView(input)
            .setPositiveButton("创建") { _, _ -> open(store.create(input.text.toString())) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun manage(file: File) {
        AlertDialog.Builder(this)
            .setItems(arrayOf("重命名", "删除")) { _, which ->
                if (which == 0) {
                    rename(file)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("删除文档？")
                        .setMessage(file.name)
                        .setPositiveButton("删除") { _, _ ->
                            store.delete(file)
                            refresh()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
            .show()
    }

    private fun rename(file: File) {
        val input = EditText(this)
        input.setText(file.nameWithoutExtension)
        AlertDialog.Builder(this)
            .setTitle("重命名文档")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                store.rename(file, input.text.toString())
                refresh()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun open(file: File) {
        startActivity(Intent(this, MarkdownEditorActivity::class.java)
            .putExtra(MarkdownEditorActivity.EXTRA_DOCUMENT_PATH, file.absolutePath))
    }
}
