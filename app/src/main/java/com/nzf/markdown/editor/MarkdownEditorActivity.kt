package com.nzf.markdown.editor

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.nzf.markdown.R
import com.nzf.markdown.document.DocumentStore
import org.json.JSONObject
import java.io.File

/**
 * Focused Markdown editor for the commercial MVP.
 *
 * The writing surface stays content-first. Formatting actions are deliberately
 * lightweight and insert portable Markdown instead of introducing a proprietary
 * rich-text model.
 */
class MarkdownEditorActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DOCUMENT_PATH = "document_path"
        private const val AUTOSAVE_DELAY_MS = 700L
    }

    private lateinit var editor: EditText
    private lateinit var preview: WebView
    private lateinit var editButton: Button
    private lateinit var previewButton: Button
    private lateinit var editorToolbar: View
    private lateinit var saveStatus: TextView
    private lateinit var titleView: TextView
    private lateinit var store: DocumentStore
    private var documentFile: File? = null
    private var previewReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { saveDocument() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_markdown_editor)

        store = DocumentStore(this)
        documentFile = intent.getStringExtra(EXTRA_DOCUMENT_PATH)?.let(::File)
        if (documentFile == null) {
            finish()
            return
        }

        editor = findViewById(R.id.et_markdown_editor)
        preview = findViewById(R.id.web_markdown_preview)
        editButton = findViewById(R.id.btn_edit_mode)
        previewButton = findViewById(R.id.btn_preview_mode)
        editorToolbar = findViewById(R.id.editor_toolbar)
        saveStatus = findViewById(R.id.tv_save_status)
        titleView = findViewById(R.id.tv_document_title)

        val file = requireDocument()
        val documentTitle = file.nameWithoutExtension
        titleView.text = if (documentTitle.trim().isEmpty()) "未命名文档" else documentTitle
        editor.setText(store.read(file))
        editor.setSelection(editor.text.length)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        preview.settings.javaScriptEnabled = true
        preview.settings.domStorageEnabled = true
        preview.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        preview.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        preview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                previewReady = true
                renderMarkdown(editor.text.toString())
            }
        }
        preview.loadUrl("file:///android_asset/editor_preview.html")

        editButton.setOnClickListener { showEditor() }
        previewButton.setOnClickListener { showPreview() }
        findViewById<Button>(R.id.btn_h1).setOnClickListener { insertAtLineStart("# ") }
        findViewById<Button>(R.id.btn_bold).setOnClickListener { wrap("**", "**") }
        findViewById<Button>(R.id.btn_italic).setOnClickListener { wrap("*", "*") }
        findViewById<Button>(R.id.btn_quote).setOnClickListener { prefixSelectedLines("> ") }
        findViewById<Button>(R.id.btn_unordered_list).setOnClickListener { prefixSelectedLines("- ") }
        findViewById<Button>(R.id.btn_ordered_list).setOnClickListener { prefixSelectedLines("1. ") }
        findViewById<Button>(R.id.btn_link).setOnClickListener { insert("[链接文本](https://)") }
        findViewById<Button>(R.id.btn_image).setOnClickListener { insert("![图片描述](https://)") }
        findViewById<Button>(R.id.btn_code).setOnClickListener { wrap("`", "`") }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                saveStatus.text = "正在保存"
                handler.removeCallbacks(autosaveRunnable)
                handler.postDelayed(autosaveRunnable, AUTOSAVE_DELAY_MS)
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })
        showEditor()
    }

    private fun showEditor() {
        editor.visibility = View.VISIBLE
        preview.visibility = View.GONE
        editorToolbar.visibility = View.VISIBLE
        editButton.isEnabled = false
        previewButton.isEnabled = true
        editor.requestFocus()
    }

    private fun showPreview() {
        saveDocument()
        renderMarkdown(editor.text.toString())
        editor.visibility = View.GONE
        preview.visibility = View.VISIBLE
        editorToolbar.visibility = View.GONE
        editButton.isEnabled = true
        previewButton.isEnabled = false
    }

    private fun insert(value: String) {
        val start = editor.selectionStart.coerceAtLeast(0)
        editor.text.insert(start, value)
        editor.setSelection((start + value.length).coerceAtMost(editor.text.length))
    }

    private fun insertAtLineStart(prefix: String) {
        val cursor = editor.selectionStart.coerceAtLeast(0)
        val content = editor.text
        var lineStart = cursor
        while (lineStart > 0 && content[lineStart - 1] != '\n') lineStart--
        content.insert(lineStart, prefix)
        editor.setSelection(cursor + prefix.length)
    }

    private fun prefixSelectedLines(prefix: String) {
        val content = editor.text
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(start)
        var lineStart = start
        while (lineStart > 0 && content[lineStart - 1] != '\n') lineStart--
        var lineEnd = end
        while (lineEnd < content.length && content[lineEnd] != '\n') lineEnd++

        val selectedText = content.substring(lineStart, lineEnd)
        val replacement = selectedText.split("\n").joinToString("\n") { line ->
            if (line.startsWith(prefix)) line else prefix + line
        }
        content.replace(lineStart, lineEnd, replacement)
        editor.setSelection(lineStart, lineStart + replacement.length)
    }

    private fun wrap(prefix: String, suffix: String) {
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(start)
        val selected = editor.text.substring(start, end)
        editor.text.replace(start, end, prefix + selected + suffix)
        editor.setSelection(start + prefix.length, start + prefix.length + selected.length)
    }

    private fun saveDocument() {
        documentFile?.let {
            store.save(it, editor.text.toString())
            saveStatus.text = "已保存"
        }
    }

    private fun requireDocument(): File = checkNotNull(documentFile)

    private fun renderMarkdown(markdown: String) {
        if (!previewReady) return
        preview.evaluateJavascript("renderMarkdown(" + JSONObject.quote(markdown) + ")", null)
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
