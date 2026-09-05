package com.nzf.markdown.editor

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.nzf.markdown.R
import com.nzf.markdown.document.DocumentStore
import org.json.JSONObject
import java.io.File

/**
 * Focused Markdown editor for the commercial MVP.
 *
 * The writing surface stays content-first. Formatting actions insert portable
 * Markdown while common list interactions behave like a real mobile editor.
 */
class MarkdownEditorActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DOCUMENT_PATH = "document_path"
        private const val AUTOSAVE_DELAY_MS = 700L
        private const val REQUEST_PICK_IMAGE = 4101
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
    private var suppressEditorWatcher = false
    private var insertedNewlineIndex = -1
    private val handler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { saveDocument() }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
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
        updateDocumentTitle(file)
        editor.setText(store.read(file))
        editor.setSelection(editor.text.length)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        titleView.setOnClickListener { showRenameDialog() }
        titleView.contentDescription = "重命名文档"

        preview.settings.javaScriptEnabled = true
        preview.settings.domStorageEnabled = true
        preview.settings.allowContentAccess = true
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
        findViewById<Button>(R.id.btn_image).setOnClickListener { pickImage() }
        findViewById<Button>(R.id.btn_code).setOnClickListener { wrap("`", "`") }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressEditorWatcher && before == 0 && count == 1 && start < s!!.length && s[start] == '\n') {
                    insertedNewlineIndex = start
                } else if (!suppressEditorWatcher) {
                    insertedNewlineIndex = -1
                }

                if (!suppressEditorWatcher) {
                    scheduleAutosave()
                }
            }

            override fun afterTextChanged(s: Editable?) {
                if (suppressEditorWatcher || s == null || insertedNewlineIndex < 0) return
                val newlineIndex = insertedNewlineIndex
                insertedNewlineIndex = -1
                continueMarkdownList(s, newlineIndex)
            }
        })
        showEditor()
    }

    private fun updateDocumentTitle(file: File) {
        val documentTitle = file.nameWithoutExtension
        titleView.text = if (documentTitle.trim().isEmpty()) "未命名文档" else documentTitle
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply {
            setSingleLine(true)
            setText(requireDocument().nameWithoutExtension)
            selectAll()
            setPadding(48, 8, 48, 8)
        }
        AlertDialog.Builder(this)
            .setTitle("重命名文档")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ -> renameDocument(input.text.toString()) }
            .show()
    }

    private fun renameDocument(name: String) {
        val current = requireDocument()
        val renamed = store.rename(current, name)
        if (renamed == null) {
            Toast.makeText(this, "名称不能为空或已存在同名文档", Toast.LENGTH_SHORT).show()
            return
        }
        documentFile = renamed
        updateDocumentTitle(renamed)
        saveStatus.text = "已重命名"
    }

    private fun showEditor() {
        editor.visibility = View.VISIBLE
        preview.visibility = View.GONE
        editorToolbar.visibility = View.VISIBLE
        editButton.isEnabled = false
        previewButton.isEnabled = true
        editButton.alpha = 1f
        previewButton.alpha = 0.55f
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
        editButton.alpha = 0.55f
        previewButton.alpha = 1f
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        persistImagePermission(uri, data.flags)
        insertImage(uri)
    }

    private fun persistImagePermission(uri: Uri, resultFlags: Int) {
        val flags = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (flags == 0) return
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
        } catch (ignored: SecurityException) {
            // Some document providers do not support persistable grants. The
            // current process can still use the URI until the provider revokes it.
        }
    }

    private fun insertImage(uri: Uri) {
        insert("![图片]($uri)")
        saveStatus.text = "图片已插入"
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

    private fun continueMarkdownList(content: Editable, newlineIndex: Int) {
        if (newlineIndex < 0 || newlineIndex > content.length) return

        var lineStart = newlineIndex
        while (lineStart > 0 && content[lineStart - 1] != '\n') lineStart--
        val previousLine = content.substring(lineStart, newlineIndex)

        val ordered = Regex("^(\\s*)(\\d+)\\.\\s*(.*)$").matchEntire(previousLine)
        if (ordered != null) {
            val indent = ordered.groupValues[1]
            val number = ordered.groupValues[2].toIntOrNull() ?: return
            val body = ordered.groupValues[3]

            suppressEditorWatcher = true
            if (body.trim().isEmpty()) {
                content.delete(lineStart, newlineIndex)
                editor.setSelection(lineStart)
            } else {
                val nextPrefix = indent + (number + 1) + ". "
                content.insert(newlineIndex + 1, nextPrefix)
                editor.setSelection(newlineIndex + 1 + nextPrefix.length)
            }
            suppressEditorWatcher = false
            scheduleAutosave()
            return
        }

        val unordered = Regex("^(\\s*)([-+*])\\s*(.*)$").matchEntire(previousLine)
        if (unordered != null) {
            val indent = unordered.groupValues[1]
            val marker = unordered.groupValues[2]
            val body = unordered.groupValues[3]

            suppressEditorWatcher = true
            if (body.trim().isEmpty()) {
                content.delete(lineStart, newlineIndex)
                editor.setSelection(lineStart)
            } else {
                val nextPrefix = indent + marker + " "
                content.insert(newlineIndex + 1, nextPrefix)
                editor.setSelection(newlineIndex + 1 + nextPrefix.length)
            }
            suppressEditorWatcher = false
            scheduleAutosave()
        }
    }

    private fun wrap(prefix: String, suffix: String) {
        val start = editor.selectionStart.coerceAtLeast(0)
        val end = editor.selectionEnd.coerceAtLeast(start)
        val selected = editor.text.substring(start, end)
        editor.text.replace(start, end, prefix + selected + suffix)
        editor.setSelection(start + prefix.length, start + prefix.length + selected.length)
    }

    private fun scheduleAutosave() {
        saveStatus.text = "正在保存…"
        handler.removeCallbacks(autosaveRunnable)
        handler.postDelayed(autosaveRunnable, AUTOSAVE_DELAY_MS)
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
