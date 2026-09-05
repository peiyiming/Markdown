package com.nzf.markdown.share

import android.app.Activity
import android.app.Application
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
 * Connects the editor share button to the rendered Markdown share pipeline.
 *
 * The existing editor is kept as the single source of truth. Before sharing we
 * save the latest text, render that exact text in the existing preview WebView,
 * wait until the DOM and images settle, then delegate to the image/PDF policy.
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
        val titleView = activity.findViewById<TextView>(R.id.tv_document_title)
        val markdown = editor.text?.toString().orEmpty()
        if (markdown.trim().isEmpty()) {
            Toast.makeText(activity, "文档内容为空，暂时无法分享", Toast.LENGTH_SHORT).show()
            return
        }

        saveLatestDocument(activity, markdown)
        val originalVisibility = preview.visibility
        if (preview.visibility == View.GONE) preview.visibility = View.INVISIBLE
        preview.post {
            val source = JSONObject.quote(markdown)
            preview.evaluateJavascript("renderMarkdown(" + source + ");") {
                waitForRenderedContent(activity, preview, titleView?.text?.toString().orEmpty(), originalVisibility, 0)
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
        preview: WebView,
        title: String,
        originalVisibility: Int,
        attempt: Int
    ) {
        val script = "(function(){var imgs=document.images||[];for(var i=0;i<imgs.length;i++){if(!imgs[i].complete)return 'waiting';}return document.readyState==='complete'?'ready':'waiting';})()"
        preview.evaluateJavascript(script, ValueCallback { value ->
            val ready = value != null && value.contains("ready")
            if (ready || attempt >= MAX_RENDER_WAIT_ATTEMPTS) {
                val safeTitle = if (title.isEmpty()) "Markdown" else title
                try {
                    if (!RenderedMarkdownShare.showShareOptions(activity, preview, safeTitle)) {
                        Toast.makeText(activity, "内容仍在渲染，请稍后重试", Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    preview.visibility = originalVisibility
                }
            } else {
                handler.postDelayed({
                    waitForRenderedContent(activity, preview, title, originalVisibility, attempt + 1)
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

    companion object {
        private const val RENDER_WAIT_INTERVAL_MS = 120L
        private const val MAX_RENDER_WAIT_ATTEMPTS = 34
    }
}
