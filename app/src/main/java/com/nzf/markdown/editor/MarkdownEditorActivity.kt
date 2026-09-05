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

class MarkdownEditorActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DOCUMENT_PATH = "document_path"
        private const val AUTOSAVE_DELAY_MS = 700L
        private const val LIVE_PREVIEW_DELAY_MS = 220L
        private const val REQUEST_PICK_IMAGE = 4101
    }

    private enum class EditorMode { EDIT, PREVIEW, LIVE }
    private enum class BlockType { ORDERED, UNORDERED, QUOTE, CODE }

    private lateinit var editor: EditText
    private lateinit var preview: WebView
    private lateinit var editButton: Button
    private lateinit var previewButton: Button
    private lateinit var liveButton: Button
    private lateinit var liveModeDivider: View
    private lateinit var editorToolbar: View
    private lateinit var saveStatus: TextView
    private lateinit var titleView: TextView
    private lateinit var store: DocumentStore
    private var documentFile: File? = null
    private var previewReady = false
    private var currentMode = EditorMode.EDIT
    private var suppressEditorWatcher = false
    private var insertedNewlineIndex = -1
    private val handler = Handler(Looper.getMainLooper())
    private val autosaveRunnable = Runnable { saveDocument() }
    private val livePreviewRunnable = Runnable { if (currentMode == EditorMode.LIVE) renderMarkdown(editor.text.toString()) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_markdown_editor)
        store = DocumentStore(this)
        documentFile = intent.getStringExtra(EXTRA_DOCUMENT_PATH)?.let(::File)
        if (documentFile == null) { finish(); return }

        editor = findViewById(R.id.et_markdown_editor)
        preview = findViewById(R.id.web_markdown_preview)
        editButton = findViewById(R.id.btn_edit_mode)
        previewButton = findViewById(R.id.btn_preview_mode)
        liveButton = findViewById(R.id.btn_live_mode)
        liveModeDivider = findViewById(R.id.live_mode_divider)
        editorToolbar = findViewById(R.id.editor_toolbar)
        saveStatus = findViewById(R.id.tv_save_status)
        titleView = findViewById(R.id.tv_document_title)

        val file = requireDocument()
        updateDocumentTitle(file)
        editor.setText(store.read(file))
        editor.setSelection(editor.text.length)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        findViewById<Button>(R.id.btn_share).setOnClickListener { shareDocument() }
        titleView.setOnClickListener { showRenameDialog() }
        titleView.contentDescription = "重命名文档"

        preview.settings.javaScriptEnabled = true
        preview.settings.domStorageEnabled = true
        preview.settings.allowContentAccess = true
        preview.settings.cacheMode = WebSettings.LOAD_NO_CACHE
        preview.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        preview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) { previewReady = true; renderMarkdown(editor.text.toString()) }
        }
        preview.loadUrl("file:///android_asset/editor_preview.html")

        editButton.setOnClickListener { showEditor() }
        previewButton.setOnClickListener { showPreview() }
        liveButton.setOnClickListener { showLivePreview() }
        findViewById<Button>(R.id.btn_h1).setOnClickListener { toggleHeading() }
        findViewById<Button>(R.id.btn_bold).setOnClickListener { toggleWrap("**", "**") }
        findViewById<Button>(R.id.btn_italic).setOnClickListener { toggleWrap("*", "*") }
        findViewById<Button>(R.id.btn_quote).setOnClickListener { toggleSelectedLines(BlockType.QUOTE) }
        findViewById<Button>(R.id.btn_unordered_list).setOnClickListener { toggleSelectedLines(BlockType.UNORDERED) }
        findViewById<Button>(R.id.btn_ordered_list).setOnClickListener { toggleSelectedLines(BlockType.ORDERED) }
        findViewById<Button>(R.id.btn_link).setOnClickListener { insertLink() }
        findViewById<Button>(R.id.btn_image).setOnClickListener { pickImage() }
        findViewById<Button>(R.id.btn_code).setOnClickListener { toggleCode() }

        editor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!suppressEditorWatcher && before == 0 && count == 1 && s != null && start < s.length && s[start] == '\n') insertedNewlineIndex = start
                else if (!suppressEditorWatcher) insertedNewlineIndex = -1
                if (!suppressEditorWatcher) { scheduleAutosave(); scheduleLivePreview() }
            }
            override fun afterTextChanged(s: Editable?) {
                if (suppressEditorWatcher || s == null || insertedNewlineIndex < 0) return
                val index = insertedNewlineIndex
                insertedNewlineIndex = -1
                continueMarkdownBlock(s, index)
            }
        })
        showEditor()
    }

    private fun updateDocumentTitle(file: File) {
        val name = file.nameWithoutExtension
        titleView.text = if (name.isEmpty()) "未命名文档" else name
    }

    private fun shareDocument() {
        val markdown = editor.text.toString()
        if (markdown.trim().isEmpty()) { Toast.makeText(this, "文档内容为空，暂时无法分享", Toast.LENGTH_SHORT).show(); return }
        saveDocument()
        val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TITLE, titleView.text.toString()); putExtra(Intent.EXTRA_TEXT, markdown) }
        try { startActivity(Intent.createChooser(intent, "分享到")) } catch (e: Exception) { Toast.makeText(this, "没有可用于分享的应用", Toast.LENGTH_SHORT).show() }
    }

    private fun showRenameDialog() {
        val input = EditText(this).apply { setSingleLine(true); setText(requireDocument().nameWithoutExtension); selectAll(); setPadding(48, 8, 48, 8) }
        AlertDialog.Builder(this).setTitle("重命名文档").setView(input).setNegativeButton("取消", null).setPositiveButton("保存") { _, _ -> renameDocument(input.text.toString()) }.show()
    }

    private fun renameDocument(name: String) {
        val renamed = store.rename(requireDocument(), name)
        if (renamed == null) { Toast.makeText(this, "名称不能为空或已存在同名文档", Toast.LENGTH_SHORT).show(); return }
        documentFile = renamed; updateDocumentTitle(renamed); saveStatus.text = "已重命名"
    }

    private fun showEditor() { currentMode = EditorMode.EDIT; editor.visibility = View.VISIBLE; preview.visibility = View.GONE; liveModeDivider.visibility = View.GONE; editorToolbar.visibility = View.VISIBLE; setModeSelection(EditorMode.EDIT); editor.requestFocus() }
    private fun showPreview() { currentMode = EditorMode.PREVIEW; handler.removeCallbacks(livePreviewRunnable); saveDocument(); renderMarkdown(editor.text.toString()); editor.visibility = View.GONE; preview.visibility = View.VISIBLE; liveModeDivider.visibility = View.GONE; editorToolbar.visibility = View.GONE; setModeSelection(EditorMode.PREVIEW) }
    private fun showLivePreview() { currentMode = EditorMode.LIVE; editor.visibility = View.VISIBLE; preview.visibility = View.VISIBLE; liveModeDivider.visibility = View.VISIBLE; editorToolbar.visibility = View.VISIBLE; setModeSelection(EditorMode.LIVE); renderMarkdown(editor.text.toString()); editor.requestFocus() }
    private fun setModeSelection(mode: EditorMode) { editButton.isEnabled = mode != EditorMode.EDIT; previewButton.isEnabled = mode != EditorMode.PREVIEW; liveButton.isEnabled = mode != EditorMode.LIVE; editButton.alpha = if (mode == EditorMode.EDIT) 1f else .55f; previewButton.alpha = if (mode == EditorMode.PREVIEW) 1f else .55f; liveButton.alpha = if (mode == EditorMode.LIVE) 1f else .55f }

    private fun toggleHeading() { toggleLinePrefix(Regex("^#{1,6}\\s+"), "# ") }
    private fun toggleLinePrefix(existing: Regex, prefix: String) {
        val content = editor.text; val start = lineStart(editor.selectionStart); val end = lineEnd(editor.selectionEnd)
        val lines = content.substring(start, end).split("\n")
        val allPrefixed = lines.all { existing.containsMatchIn(it) }
        val replacement = lines.joinToString("\n") { if (allPrefixed) it.replaceFirst(existing, "") else prefix + it }
        replaceSelection(start, end, replacement, start, start + replacement.length)
    }

    private fun toggleSelectedLines(type: BlockType) {
        val content = editor.text; val start = lineStart(editor.selectionStart); val end = lineEnd(editor.selectionEnd)
        val lines = content.substring(start, end).split("\n")
        val pattern = when (type) { BlockType.QUOTE -> Regex("^\\s*>\\s?"); BlockType.UNORDERED -> Regex("^(\\s*)[-+*]\\s+"); BlockType.ORDERED -> Regex("^(\\s*)\\d+[.)]\\s+"); else -> return }
        val allPrefixed = lines.all { pattern.containsMatchIn(it) }
        val replacement = lines.mapIndexed { index, line ->
            if (allPrefixed) line.replaceFirst(pattern, "") else when (type) {
                BlockType.QUOTE -> "> " + line
                BlockType.UNORDERED -> "- " + line
                BlockType.ORDERED -> "${index + 1}. " + line
                else -> line
            }
        }.joinToString("\n")
        replaceSelection(start, end, replacement, start, start + replacement.length)
    }

    private fun toggleWrap(prefix: String, suffix: String) {
        val start = editor.selectionStart.coerceAtLeast(0); val end = editor.selectionEnd.coerceAtLeast(start); val content = editor.text
        if (start >= prefix.length && end + suffix.length <= content.length && content.substring(start - prefix.length, start) == prefix && content.substring(end, end + suffix.length) == suffix) {
            suppressEditorWatcher = true; content.delete(end, end + suffix.length); content.delete(start - prefix.length, start); editor.setSelection(start - prefix.length, end - prefix.length); suppressEditorWatcher = false; notifyEditorMutation(); return
        }
        val selected = content.substring(start, end)
        if (selected.isEmpty()) { suppressEditorWatcher = true; content.insert(start, prefix + suffix); editor.setSelection(start + prefix.length); suppressEditorWatcher = false }
        else replaceSelection(start, end, prefix + selected + suffix, start + prefix.length, start + prefix.length + selected.length)
        notifyEditorMutation()
    }

    private fun toggleCode() {
        val start = editor.selectionStart; val end = editor.selectionEnd
        if (start != end && editor.text.substring(start, end).contains("\n")) {
            val selected = editor.text.substring(start, end); replaceSelection(start, end, "```\n$selected\n```", start + 4, start + 4 + selected.length)
        } else toggleWrap("`", "`")
    }

    private fun insertLink() {
        val start = editor.selectionStart; val end = editor.selectionEnd; val selected = editor.text.substring(start, end)
        if (selected.isEmpty()) { replaceSelection(start, end, "[链接文本](https://)", start + 1, start + 5) }
        else replaceSelection(start, end, "[$selected](https://)", start + selected.length + 3, start + selected.length + 11)
    }

    private fun pickImage() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, REQUEST_PICK_IMAGE) }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK) return; val uri = data?.data ?: return; persistImagePermission(uri, data.flags); insertImage(uri) }
    private fun persistImagePermission(uri: Uri, resultFlags: Int) { val flags = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION; if (flags != 0) try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) {} }
    private fun insertImage(uri: Uri) { val start = editor.selectionStart; val prefix = if (start > 0 && editor.text[start - 1] != '\n') "\n" else ""; val value = prefix + "![图片]($uri)\n"; editor.text.insert(start, value); editor.setSelection(start + value.length); saveStatus.text = "图片已插入" }

    private fun continueMarkdownBlock(content: Editable, newlineIndex: Int) {
        val previous = content.substring(lineStart(newlineIndex), newlineIndex)
        val ordered = Regex("^(\\s*)(\\d+)([.)])\\s*(.*)$").matchEntire(previous)
        if (ordered != null) { continueBlock(content, newlineIndex, ordered.groupValues[4], ordered.groupValues[1], ordered.groupValues[1] + ((ordered.groupValues[2].toIntOrNull() ?: 1) + 1) + ordered.groupValues[3] + " "); return }
        val unordered = Regex("^(\\s*)([-+*])\\s*(.*)$").matchEntire(previous)
        if (unordered != null) { continueBlock(content, newlineIndex, unordered.groupValues[3], unordered.groupValues[1], unordered.groupValues[1] + unordered.groupValues[2] + " "); return }
        val quote = Regex("^(\\s*>\\s?)(.*)$").matchEntire(previous)
        if (quote != null) { continueBlock(content, newlineIndex, quote.groupValues[2], "", quote.groupValues[1]); return }
        if (isInsideCodeFence(newlineIndex)) { return }
    }

    private fun continueBlock(content: Editable, newlineIndex: Int, body: String, removePrefix: String, nextPrefix: String) {
        suppressEditorWatcher = true
        if (body.trim().isEmpty()) { val removeStart = (newlineIndex - removePrefix.length).coerceAtLeast(0); content.delete(removeStart, newlineIndex); editor.setSelection(removeStart) }
        else { content.insert(newlineIndex + 1, nextPrefix); editor.setSelection(newlineIndex + 1 + nextPrefix.length) }
        suppressEditorWatcher = false; notifyEditorMutation()
    }

    private fun isInsideCodeFence(position: Int): Boolean { val before = editor.text.substring(0, position); return Regex("(?m)^```.*$").findAll(before).count() % 2 == 1 }
    private fun lineStart(position: Int): Int { var p = position.coerceIn(0, editor.text.length); while (p > 0 && editor.text[p - 1] != '\n') p--; return p }
    private fun lineEnd(position: Int): Int { var p = position.coerceIn(0, editor.text.length); while (p < editor.text.length && editor.text[p] != '\n') p++; return p }
    private fun replaceSelection(start: Int, end: Int, value: String, selectionStart: Int, selectionEnd: Int) { suppressEditorWatcher = true; editor.text.replace(start, end, value); editor.setSelection(selectionStart.coerceIn(0, editor.text.length), selectionEnd.coerceIn(0, editor.text.length)); suppressEditorWatcher = false; notifyEditorMutation() }
    private fun notifyEditorMutation() { scheduleAutosave(); scheduleLivePreview() }

    private fun scheduleAutosave() { saveStatus.text = "正在保存…"; handler.removeCallbacks(autosaveRunnable); handler.postDelayed(autosaveRunnable, AUTOSAVE_DELAY_MS) }
    private fun scheduleLivePreview() { if (currentMode != EditorMode.LIVE) return; handler.removeCallbacks(livePreviewRunnable); handler.postDelayed(livePreviewRunnable, LIVE_PREVIEW_DELAY_MS) }
    private fun saveDocument() { documentFile?.let { store.save(it, editor.text.toString()); saveStatus.text = "已保存" } }
    private fun requireDocument(): File = checkNotNull(documentFile)
    private fun renderMarkdown(markdown: String) { if (previewReady) preview.evaluateJavascript("renderMarkdown(" + JSONObject.quote(markdown) + ")", null) }
    override fun onPause() { saveDocument(); super.onPause() }
    override fun onDestroy() { handler.removeCallbacks(autosaveRunnable); handler.removeCallbacks(livePreviewRunnable); preview.loadUrl("about:blank"); preview.destroy(); super.onDestroy() }
}