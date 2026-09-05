package com.nzf.markdown.editor

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import com.nzf.markdown.R
import java.io.File

/** First functional Markdown editor with local persistence and autosave. */
class MarkdownEditorActivity : AppCompatActivity() {
    companion object { const val EXTRA_DOCUMENT_PATH = "document_path" }

    private lateinit var editor: EditText
    private lateinit var preview: WebView
    private lateinit var editButton: Button
    private lateinit var previewButton: Button
    private lateinit var store: DocumentStore
    private var documentPath: String? = null
    private var previewReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { saveDocument() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_markdown_editor)
        store = DocumentStore(this)
        documentPath = intent.getStringExtra(EXTRA_DOCUMENT_PATH)

        editor = findViewById(R.id.et_markdown_editor)
        preview = findViewById(R.id.web_markdown_preview)
        editButton = findViewById(R.id.btn_edit_mode)
        previewButton = findViewById(R.id.btn_preview_mode)
        documentPath?.let { editor.setText(store.load(it)) }

        preview.settings.javaScriptEnabled = true
        preview.settings.domStorageEnabled = true
        preview.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        preview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                previewReady = true
                renderMarkdown(editor.text.toString())
            }
        }
        preview.loadUrl("file:///android_asset/editor_preview.html")

        editButton.setOnClickListener { showEditor() }
        previewButton.setOnClickListener { showPreview() }
        findViewById<Button>(R.id.btn_h1).setOnClickListener { insert("# ") }
        findViewById<Button>(R.id.btn_bold).setOnClickListener { wrap("**", "**") }
        findViewById<Button>(R.id.btn_italic).setOnClickListener { wrap("*", "*") }
        findViewById<Button>(R.id.btn_link).setOnClickListener { insert("[text](https://)") }
        findViewById<Button>(R.id.btn_code).setOnClickListener { wrap("`", "`") }
        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                handler.removeCallbacks(autosaveRunnable)
                handler.postDelayed(autosaveRunnable, 700)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        showEditor()
    }

    private fun showEditor() {
        editor.visibility = View.VISIBLE
        preview.visibility = View.GONE
        editButton.isEnabled = false
        previewButton.isEnabled = true
    }

    private fun showPreview() {
        saveDocument()
        renderMarkdown(editor.text.toString())
        editor.visibility = View.GONE
        preview.visibility = View.VISIBLE
        editButton.isEnabled = true
        previewButton.isEnabled = false
    }

    private fun insert(value: String) {
        val start = editor.selectionStart.coerceAtLeast(0)
        editor.text.insert(start, value)
        editor.setSelection((start + value.length).coerceAtMost(editor.text.length))
    }

    private fun wrap(prefix: String, suffix: String) {
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(start)
        val selected = editor.text.substring(start, end)
        editor.text.replace(start, end, prefix + selected + suffix)
        editor.setSelection(start + prefix.length, start + prefix.length + selected.length)
    }

    private fun saveDocument() {
        val path = documentPath ?: return
        store.save(path, editor.text.toString())
    }

    private fun renderMarkdown(markdown: String) {
        if (!previewReady) return
        val escaped = markdown.replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        preview.evaluateJavascript("renderMarkdown('$escaped')", null)
    }

    override fun onPause() {
        saveDocument()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(autosaveRunnable)
        preview.loadUrl("about:blank")
        preview.destroy()
        super.onDestroy()
    }
}
