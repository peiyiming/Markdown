package com.nzf.markdown.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import com.nzf.markdown.R

/**
 * MVP Markdown editor.
 *
 * Uses a native EditText for authoring and the repository's existing
 * marked.js/result.html assets for preview rendering.
 */
class MarkdownEditorActivity : AppCompatActivity() {

    private lateinit var editor: EditText
    private lateinit var preview: WebView
    private lateinit var editButton: Button
    private lateinit var previewButton: Button

    private var previewReady = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_markdown_editor)

        editor = findViewById(R.id.et_markdown_editor)
        preview = findViewById(R.id.web_markdown_preview)
        editButton = findViewById(R.id.btn_edit_mode)
        previewButton = findViewById(R.id.btn_preview_mode)

        preview.settings.javaScriptEnabled = true
        preview.settings.domStorageEnabled = true
        preview.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        preview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                previewReady = true
                renderMarkdown(editor.text.toString())
            }
        }
        preview.loadUrl("file:///android_asset/result.html")

        editButton.setOnClickListener { showEditor() }
        previewButton.setOnClickListener { showPreview() }

        findViewById<Button>(R.id.btn_h1).setOnClickListener { insert("# ") }
        findViewById<Button>(R.id.btn_bold).setOnClickListener { wrap("**", "**") }
        findViewById<Button>(R.id.btn_italic).setOnClickListener { wrap("*", "*") }
        findViewById<Button>(R.id.btn_link).setOnClickListener { insert("[text](https://)") }
        findViewById<Button>(R.id.btn_code).setOnClickListener { wrap("`", "`") }
    }

    private fun showEditor() {
        editor.visibility = View.VISIBLE
        preview.visibility = View.GONE
        editButton.isEnabled = false
        previewButton.isEnabled = true
    }

    private fun showPreview() {
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

    private fun renderMarkdown(markdown: String) {
        if (!previewReady) return
        val escaped = markdown
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "\\n")
            .replace("\r", "")
        preview.evaluateJavascript("renderMarkdown('$escaped')", null)
    }

    override fun onDestroy() {
        preview.loadUrl("about:blank")
        preview.destroy()
        super.onDestroy()
    }
}
