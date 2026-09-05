package com.nzf.markdown.editor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Rect
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
import android.view.inputmethod.InputMethodManager
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
        private const val STATE_EDITOR_MODE = "editor_mode"
        private const val STATE_SELECTION_START = "selection_start"
        private const val STATE_SELECTION_END = "selection_end"
        private const val STATE_EDITOR_SCROLL_X = "editor_scroll_x"
        private const val STATE_EDITOR_SCROLL_Y = "editor_scroll_y"
        private const val STATE_PREVIEW_SCROLL_Y = "preview_scroll_y"
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
    private var pendingEditorScrollX = 0
    private var pendingEditorScrollY = 0
    private var pendingPreviewScrollY: Int? = null
    private var pendingSelectionStart = -1
    private var pendingSelectionEnd = -1
    private var pendingRestoredMode: EditorMode? = null
    private var restoringState = false
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
        restorePendingState(savedInstanceState)

        findViewById<Button>(R.id.btn_back).setOnClickListener { handleBackNavigation() }
        findViewById<Button>(R.id.btn_share).setOnClickListener { shareDocument() }
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
                applyRestoredMode()
            }
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
                if (!suppressEditorWatcher && !restoringState) { scheduleAutosave(); scheduleLivePreview() }
            }
            override fun afterTextChanged(s: Editable?) {
                if (suppressEditorWatcher || s == null || insertedNewlineIndex < 0) return
                val index = insertedNewlineIndex
                insertedNewlineIndex = -1
                continueMarkdownBlock(s, index)
            }
        })
        if (savedInstanceState == null) showEditor() else applyRestoredMode()
    }

    private fun restorePendingState(state: Bundle?) {
        if (state == null) return
        restoringState = true
        val restoredModeName = state.getString(STATE_EDITOR_MODE)
        pendingRestoredMode = try {
            if (restoredModeName.isNullOrEmpty()) EditorMode.EDIT else EditorMode.valueOf(restoredModeName)
        } catch (_: IllegalArgumentException) {
            EditorMode.EDIT
        }
        pendingSelectionStart = state.getInt(STATE_SELECTION_START, editor.text.length).coerceIn(0, editor.text.length)
        pendingSelectionEnd = state.getInt(STATE_SELECTION_END, pendingSelectionStart).coerceIn(0, editor.text.length)
        pendingEditorScrollX = state.getInt(STATE_EDITOR_SCROLL_X, 0)
        pendingEditorScrollY = state.getInt(STATE_EDITOR_SCROLL_Y, 0)
        pendingPreviewScrollY = if (state.containsKey(STATE_PREVIEW_SCROLL_Y)) state.getInt(STATE_PREVIEW_SCROLL_Y) else null
        editor.post {
            editor.setSelection(pendingSelectionStart, pendingSelectionEnd)
            editor.scrollTo(pendingEditorScrollX, pendingEditorScrollY)
            restoringState = false
        }
    }

    private fun applyRestoredMode() {
        val mode = pendingRestoredMode ?: return
        pendingRestoredMode = null
        when (mode) {
            EditorMode.EDIT -> showEditor(false)
            EditorMode.PREVIEW -> showPreview(false)
            EditorMode.LIVE -> showLivePreview(false)
        }
        restoreEditorViewport()
    }

    private fun restoreEditorViewport() {
        if (pendingSelectionStart < 0) return
        editor.post {
            editor.setSelection(
                pendingSelectionStart.coerceIn(0, editor.text.length),
                pendingSelectionEnd.coerceIn(0, editor.text.length)
            )
            editor.scrollTo(pendingEditorScrollX, pendingEditorScrollY)
        }
    }

    private fun updateDocumentTitle(file: File) { val name = file.nameWithoutExtension; titleView.text = if (name.isEmpty()) "未命名文档" else name }
    private fun shareDocument() { val markdown = editor.text.toString(); if (markdown.trim().isEmpty()) { Toast.makeText(this, "文档内容为空，暂时无法分享", Toast.LENGTH_SHORT).show(); return }; saveDocument(); val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TITLE, titleView.text.toString()); putExtra(Intent.EXTRA_TEXT, markdown) }; try { startActivity(Intent.createChooser(intent, "分享到")) } catch (e: Exception) { Toast.makeText(this, "没有可用于分享的应用", Toast.LENGTH_SHORT).show() } }
    private fun showRenameDialog() { val input = EditText(this).apply { setSingleLine(true); setText(requireDocument().nameWithoutExtension); selectAll(); setPadding(48, 8, 48, 8) }; AlertDialog.Builder(this).setTitle("重命名文档").setView(input).setNegativeButton("取消", null).setPositiveButton("保存") { _, _ -> renameDocument(input.text.toString()) }.show() }
    private fun renameDocument(name: String) { val renamed = store.rename(requireDocument(), name); if (renamed == null) { Toast.makeText(this, "名称不能为空或已存在同名文档", Toast.LENGTH_SHORT).show(); return }; documentFile = renamed; updateDocumentTitle(renamed); saveStatus.text = "已重命名" }
    private fun showEditor(requestFocus: Boolean = true) { currentMode = EditorMode.EDIT; editor.visibility = View.VISIBLE; preview.visibility = View.GONE; liveModeDivider.visibility = View.GONE; editorToolbar.visibility = View.VISIBLE; setModeSelection(EditorMode.EDIT); if (requestFocus) editor.requestFocus() }
    private fun showPreview(saveBeforeRender: Boolean = true) { currentMode = EditorMode.PREVIEW; handler.removeCallbacks(livePreviewRunnable); if (saveBeforeRender) saveDocument(); renderMarkdown(editor.text.toString()); editor.visibility = View.GONE; preview.visibility = View.VISIBLE; liveModeDivider.visibility = View.GONE; editorToolbar.visibility = View.GONE; setModeSelection(EditorMode.PREVIEW) }
    private fun showLivePreview(requestFocus: Boolean = true) { currentMode = EditorMode.LIVE; editor.visibility = View.VISIBLE; preview.visibility = View.VISIBLE; liveModeDivider.visibility = View.VISIBLE; editorToolbar.visibility = View.VISIBLE; setModeSelection(EditorMode.LIVE); renderMarkdown(editor.text.toString()); if (requestFocus) editor.requestFocus() }
    private fun setModeSelection(mode: EditorMode) { editButton.isEnabled = mode != EditorMode.EDIT; previewButton.isEnabled = mode != EditorMode.PREVIEW; liveButton.isEnabled = mode != EditorMode.LIVE; editButton.alpha = if (mode == EditorMode.EDIT) 1f else .55f; previewButton.alpha = if (mode == EditorMode.PREVIEW) 1f else .55f; liveButton.alpha = if (mode == EditorMode.LIVE) 1f else .55f }

    private fun toggleHeading() { val content = editor.text; val start = lineStart(editor.selectionStart); val end = lineEnd(editor.selectionEnd); val heading = Regex("^(#{1,6})\\s+"); val lines = content.substring(start, end).split("\n"); val allH1 = lines.all { heading.find(it)?.groupValues?.get(1) == "#" }; val replacement = lines.joinToString("\n") { line -> if (allH1) line.replaceFirst(heading, "") else "# " + line.replaceFirst(heading, "") }; replaceSelection(start, end, replacement, start, start + replacement.length) }
    private fun toggleSelectedLines(type: BlockType) { val content = editor.text; val start = lineStart(editor.selectionStart); val end = lineEnd(editor.selectionEnd); val lines = content.substring(start, end).split("\n"); val pattern = when (type) { BlockType.QUOTE -> Regex("^\\s*>\\s?"); BlockType.UNORDERED -> Regex("^(\\s*)[-+*]\\s+"); BlockType.ORDERED -> Regex("^(\\s*)\\d+[.)]\\s+"); else -> return }; val allPrefixed = lines.all { pattern.containsMatchIn(it) }; val replacement = lines.mapIndexed { index, line -> if (allPrefixed) line.replaceFirst(pattern, "") else when (type) { BlockType.QUOTE -> "> " + line; BlockType.UNORDERED -> "- " + line; BlockType.ORDERED -> "${index + 1}. " + line; else -> line } }.joinToString("\n"); replaceSelection(start, end, replacement, start, start + replacement.length) }
    private fun toggleWrap(prefix: String, suffix: String) { val start = editor.selectionStart.coerceAtLeast(0); val end = editor.selectionEnd.coerceAtLeast(start); val content = editor.text; val selected = content.substring(start, end); if (selected.isNotEmpty() && selected.startsWith(prefix) && selected.endsWith(suffix) && selected.length >= prefix.length + suffix.length) { val unwrapped = selected.substring(prefix.length, selected.length - suffix.length); replaceSelection(start, end, unwrapped, start, start + unwrapped.length); return }; if (isSelectionWrapped(content, start, end, prefix, suffix)) { suppressEditorWatcher = true; content.delete(end, end + suffix.length); content.delete(start - prefix.length, start); editor.setSelection(start - prefix.length, end - prefix.length); suppressEditorWatcher = false; notifyEditorMutation(); return }; if (selected.isEmpty()) { suppressEditorWatcher = true; content.insert(start, prefix + suffix); editor.setSelection(start + prefix.length); suppressEditorWatcher = false; notifyEditorMutation() } else replaceSelection(start, end, prefix + selected + suffix, start + prefix.length, start + prefix.length + selected.length) }
    private fun isSelectionWrapped(content: Editable, start: Int, end: Int, prefix: String, suffix: String): Boolean { if (start < prefix.length || end + suffix.length > content.length) return false; if (content.substring(start - prefix.length, start) != prefix || content.substring(end, end + suffix.length) != suffix) return false; if (prefix == "*" && suffix == "*") { val before = start - 2; val after = end + 1; if (before >= 0 && content[before] == '*') return false; if (after < content.length && content[after] == '*') return false }; return true }

    private fun toggleCode() {
        val start = editor.selectionStart
        val end = editor.selectionEnd
        val selected = editor.text.substring(start, end)
        if (selected.contains("\n")) {
            if (selected.startsWith("```\n") && selected.endsWith("\n```")) {
                val unwrapped = selected.substring(4, selected.length - 4)
                replaceSelection(start, end, unwrapped, start, start + unwrapped.length)
            } else replaceSelection(start, end, "```\n$selected\n```", start + 4, start + 4 + selected.length)
            return
        }
        if (selected.isNotEmpty()) { toggleWrap("`", "`"); return }
        if (lineStart(start) == lineEnd(start)) insertCodeBlock(start) else toggleWrap("`", "`")
    }

    private fun insertCodeBlock(start: Int) { val value = "```\n\n```"; replaceSelection(start, start, value, start + 4, start + 4) }
    private fun insertLink() { val start = editor.selectionStart; val end = editor.selectionEnd; val selected = editor.text.substring(start, end); if (selected.isEmpty()) { val label = "链接文本"; val url = "https://"; val value = "[$label]($url)"; replaceSelection(start, end, value, start + 1, start + 1 + label.length) } else { val url = "https://"; val value = "[$selected]($url)"; val urlStart = start + selected.length + 3; replaceSelection(start, end, value, urlStart, urlStart + url.length) } }
    private fun pickImage() { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) }, REQUEST_PICK_IMAGE) }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) { super.onActivityResult(requestCode, resultCode, data); if (requestCode != REQUEST_PICK_IMAGE || resultCode != RESULT_OK) return; val uri = data?.data ?: return; persistImagePermission(uri, data.flags); insertImage(uri) }
    private fun persistImagePermission(uri: Uri, resultFlags: Int) { val flags = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION; if (flags != 0) try { contentResolver.takePersistableUriPermission(uri, flags) } catch (_: SecurityException) {} }
    private fun insertImage(uri: Uri) { val start = editor.selectionStart; val prefix = if (start > 0 && editor.text[start - 1] != '\n') "\n" else ""; val value = prefix + "![图片]($uri)\n"; editor.text.insert(start, value); editor.setSelection(start + value.length); saveStatus.text = "图片已插入"; notifyEditorMutation() }

    private fun continueMarkdownBlock(content: Editable, newlineIndex: Int) {
        if (isInsideCodeFence(newlineIndex)) { continueCodeFence(content, newlineIndex); return }
        val previousLineStart = lineStart(newlineIndex)
        val previous = content.substring(previousLineStart, newlineIndex)
        val ordered = Regex("^(\\s*)(\\d+)([.)])\\s*(.*)$").matchEntire(previous)
        if (ordered != null) { continueListBlock(content, previousLineStart, newlineIndex, ordered.groupValues[4], ordered.groupValues[1], "${ordered.groupValues[2].toIntOrNull()?.plus(1) ?: 2}${ordered.groupValues[3]} "); return }
        val unordered = Regex("^(\\s*)([-+*])\\s*(.*)$").matchEntire(previous)
        if (unordered != null) { continueListBlock(content, previousLineStart, newlineIndex, unordered.groupValues[3], unordered.groupValues[1], unordered.groupValues[2] + " "); return }
        val quote = Regex("^(\\s*(?:>\\s?)+)(.*)$").matchEntire(previous)
        if (quote != null) continueQuoteBlock(content, previousLineStart, newlineIndex, quote.groupValues[2], quote.groupValues[1])
    }

    private fun continueCodeFence(content: Editable, newlineIndex: Int) { val previousLineStart = lineStart(newlineIndex); val previous = content.substring(previousLineStart, newlineIndex); if (previous.isNotEmpty()) return; suppressEditorWatcher = true; val insertion = newlineIndex + 1; content.insert(insertion, "```\n"); editor.setSelection(insertion + 4); suppressEditorWatcher = false; notifyEditorMutation() }
    private fun continueListBlock(content: Editable, lineStart: Int, newlineIndex: Int, body: String, indent: String, nextMarker: String) { suppressEditorWatcher = true; if (body.trim().isEmpty()) { if (indent.isNotEmpty()) { content.replace(lineStart, newlineIndex + 1, indent); editor.setSelection(lineStart + indent.length) } else { content.delete(lineStart, newlineIndex + 1); editor.setSelection(lineStart) } } else { val prefix = indent + nextMarker; content.insert(newlineIndex + 1, prefix); editor.setSelection(newlineIndex + 1 + prefix.length) }; suppressEditorWatcher = false; notifyEditorMutation() }
    private fun continueQuoteBlock(content: Editable, lineStart: Int, newlineIndex: Int, body: String, prefix: String) { suppressEditorWatcher = true; if (body.trim().isEmpty()) { val parent = quoteParentPrefix(prefix); if (parent.isEmpty()) { content.delete(lineStart, newlineIndex + 1); editor.setSelection(lineStart) } else { content.replace(lineStart, newlineIndex + 1, parent); editor.setSelection(lineStart + parent.length) } } else { val nextPrefix = normalizeQuotePrefix(prefix); content.insert(newlineIndex + 1, nextPrefix); editor.setSelection(newlineIndex + 1 + nextPrefix.length) }; suppressEditorWatcher = false; notifyEditorMutation() }

    private fun quoteParentPrefix(prefix: String): String { val indent = prefix.takeWhile { it == ' ' || it == '\t' }; val count = Regex(">").findAll(prefix.substring(indent.length)).count(); if (count <= 1) return ""; return indent + (1 until count).joinToString("") { "> " } }
    private fun normalizeQuotePrefix(prefix: String): String { val indent = prefix.takeWhile { it == ' ' || it == '\t' }; val count = Regex(">").findAll(prefix.substring(indent.length)).count(); return indent + (1..count).joinToString("") { "> " } }
    private fun isInsideCodeFence(position: Int): Boolean { val before = editor.text.substring(0, position.coerceIn(0, editor.text.length)); return Regex("(?m)^```.*$").findAll(before).count() % 2 == 1 }
    private fun lineStart(position: Int): Int { var p = position.coerceIn(0, editor.text.length); while (p > 0 && editor.text[p - 1] != '\n') p--; return p }
    private fun lineEnd(position: Int): Int { var p = position.coerceIn(0, editor.text.length); while (p < editor.text.length && editor.text[p] != '\n') p++; return p }
    private fun replaceSelection(start: Int, end: Int, value: String, selectionStart: Int, selectionEnd: Int) { suppressEditorWatcher = true; editor.text.replace(start, end, value); editor.setSelection(selectionStart.coerceIn(0, editor.text.length), selectionEnd.coerceIn(0, editor.text.length)); suppressEditorWatcher = false; notifyEditorMutation() }
    private fun notifyEditorMutation() { scheduleAutosave(); scheduleLivePreview() }
    private fun scheduleAutosave() { handler.removeCallbacks(autosaveRunnable); saveStatus.text = "编辑中"; handler.postDelayed(autosaveRunnable, AUTOSAVE_DELAY_MS) }
    private fun scheduleLivePreview() { handler.removeCallbacks(livePreviewRunnable); if (currentMode == EditorMode.LIVE) handler.postDelayed(livePreviewRunnable, LIVE_PREVIEW_DELAY_MS) }
    private fun saveDocument() { val file = documentFile ?: return; try { store.write(file, editor.text.toString()); saveStatus.text = "已保存" } catch (e: Exception) { saveStatus.text = "保存失败" } }

    private fun renderMarkdown(markdown: String) {
        if (!previewReady) return
        val encoded = JSONObject.quote(markdown)
        preview.evaluateJavascript("renderMarkdown($encoded);", null)
        restorePreviewScrollWhenReady()
    }

    private fun restorePreviewScrollWhenReady() {
        val target = pendingPreviewScrollY ?: return
        preview.postDelayed({
            if (!previewReady) return@postDelayed
            val script = "(function(){window.scrollTo(0, " + target.coerceAtLeast(0) + "); return window.scrollY || window.pageYOffset || 0;})()"
            preview.evaluateJavascript(script) { pendingPreviewScrollY = null }
        }, 120)
    }

    private fun handleBackNavigation() {
        if (isKeyboardVisible()) {
            hideKeyboard()
            return
        }
        saveDocument()
        finish()
    }

    private fun isKeyboardVisible(): Boolean {
        val visibleFrame = Rect()
        window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
        val rootHeight = window.decorView.rootView.height
        return rootHeight > 0 && rootHeight - visibleFrame.bottom > rootHeight / 6
    }

    private fun hideKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(editor.windowToken, 0)
        editor.clearFocus()
    }

    override fun onBackPressed() {
        if (isKeyboardVisible()) {
            hideKeyboard()
            return
        }
        saveDocument()
        super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_EDITOR_MODE, currentMode.name)
        outState.putInt(STATE_SELECTION_START, editor.selectionStart.coerceAtLeast(0))
        outState.putInt(STATE_SELECTION_END, editor.selectionEnd.coerceAtLeast(0))
        outState.putInt(STATE_EDITOR_SCROLL_X, editor.scrollX)
        outState.putInt(STATE_EDITOR_SCROLL_Y, editor.scrollY)
        if (previewReady) {
            preview.evaluateJavascript("(function(){return String(window.scrollY || window.pageYOffset || 0);})()") { value ->
                value.trim().trim('"').toIntOrNull()?.let { pendingPreviewScrollY = it }
            }
        }
        pendingPreviewScrollY?.let { outState.putInt(STATE_PREVIEW_SCROLL_Y, it) }
        super.onSaveInstanceState(outState)
    }

    private fun requireDocument(): File = documentFile ?: throw IllegalStateException("Document is missing")
    override fun onPause() { super.onPause(); saveDocument() }
    override fun onDestroy() { handler.removeCallbacks(autosaveRunnable); handler.removeCallbacks(livePreviewRunnable); super.onDestroy() }
}
