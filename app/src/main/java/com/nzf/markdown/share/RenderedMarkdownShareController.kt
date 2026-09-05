package com.nzf.markdown.share

import android.app.Activity
import android.app.Application
import android.app.ProgressDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import com.nzf.markdown.R
import com.nzf.markdown.document.DocumentStore
import com.nzf.markdown.editor.MarkdownEditorActivity
import org.json.JSONObject
import java.io.File

/**
 * Connects the editor share button to the rendered Markdown export pipeline.
 *
 * Export is performed from a real, visible WebView. The previous implementation
 * accidentally referenced the editor from a callback where it was out of scope,
 * causing the Kotlin build failure. Keeping the editor in the explicit export
 * state also makes UI restoration deterministic after image/PDF generation.
 */
class RenderedMarkdownShareController : Application.ActivityLifecycleCallbacks {
    private val handler = Handler(Looper.getMainLooper())

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        if (activity !is MarkdownEditorActivity) return
        activity.window.decorView.post {
            val shareButton = activity.findViewById<Button>(R.id.btn_share) ?: return@post
            shareButton.setOnClickListener { shareCurrentDocument(activity) }
        }
    }

    private fun shareCurrentDocument(activity: MarkdownEditorActivity) {
        val editor = activity.findViewById<EditText>(R.id.et_markdown_editor) ?: return
        val preview = activity.findViewById<WebView>(R.id.web_markdown_preview) ?: return
        val divider = activity.findViewById<View>(R.id.live_mode_divider)
        val titleView = activity.findViewById<TextView>(R.id.tv_document_title)
        val markdown = editor.text?.toString().orEmpty()
        if (markdown.trim().isEmpty()) {
            Toast.makeText(activity, "文档内容为空，暂时无法分享", Toast.LENGTH_SHORT).show()
            return
        }

        saveLatestDocument(activity, markdown)
        val state = ExportUiState(editor.visibility, preview.visibility, divider?.visibility ?: View.GONE)
        val progress = ProgressDialog(activity).apply {
            setMessage("正在渲染完整文档，请稍候…")
            setCancelable(false)
            setCanceledOnTouchOutside(false)
        }
        progress.show()

        editor.visibility = View.GONE
        divider?.visibility = View.GONE
        preview.alpha = 1f
        preview.translationX = 0f
        preview.visibility = View.VISIBLE
        preview.requestLayout()

        preview.post {
            val source = JSONObject.quote(markdown)
            preview.evaluateJavascript("renderMarkdown(" + source + ");") {
                waitForRenderedContent(
                    activity,
                    editor,
                    preview,
                    titleView?.text?.toString().orEmpty(),
                    state,
                    progress,
                    0
                )
            }
        }
    }

    private fun saveLatestDocument(activity: MarkdownEditorActivity, markdown: String) {
        val path = activity.intent.getStringExtra(MarkdownEditorActivity.EXTRA_DOCUMENT_PATH) ?: return
        try {
            DocumentStore(activity).save(File(path), markdown)
        } catch (_: Exception) {
            Toast.makeText(activity, "保存失败，将继续使用当前内容生成分享文件", Toast.LENGTH_SHORT).show()
        }
    }

    private fun waitForRenderedContent(
        activity: MarkdownEditorActivity,
        editor: EditText,
        preview: WebView,
        title: String,
        state: ExportUiState,
        progress: ProgressDialog,
        attempt: Int
    ) {
        val script = "(function(){var root=document.getElementById('content')||document.body;if(!root||root.scrollHeight<=0)return 'waiting';var imgs=document.images||[];for(var i=0;i<imgs.length;i++){if(!imgs[i].complete||imgs[i].naturalWidth===0)return 'waiting';}return 'ready';})()"
        preview.evaluateJavascript(script, ValueCallback { value ->
            val ready = value != null && value.contains("ready")
            if (ready || attempt >= MAX_RENDER_WAIT_ATTEMPTS) {
                preview.postDelayed({
                    if (progress.isShowing) progress.dismiss()
                    val safeTitle = if (title.isEmpty()) "Markdown" else title
                    val restoreUi = {
                        if (!activity.isFinishing) {
                            preview.scrollTo(0, 0)
                            editor.visibility = state.editorVisibility
                            preview.visibility = state.previewVisibility
                            activity.findViewById<View>(R.id.live_mode_divider)?.visibility = state.dividerVisibility
                        }
                    }
                    val shown = RenderedMarkdownShare.showShareOptions(activity, preview, safeTitle, restoreUi)
                    if (!shown) {
                        restoreUi.invoke()
                        Toast.makeText(activity, "内容渲染失败，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                }, FINAL_RENDER_SETTLE_DELAY_MS)
            } else {
                handler.postDelayed({
                    waitForRenderedContent(activity, editor, preview, title, state, progress, attempt + 1)
                }, RENDER_WAIT_INTERVAL_MS)
            }
        })
    }

    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private data class ExportUiState(
        val editorVisibility: Int,
        val previewVisibility: Int,
        val dividerVisibility: Int
    )

    companion object {
        private const val RENDER_WAIT_INTERVAL_MS = 120L
        private const val MAX_RENDER_WAIT_ATTEMPTS = 50
        private const val FINAL_RENDER_SETTLE_DELAY_MS = 250L
    }
}
