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
 * Android does not guarantee drawing for an INVISIBLE WebView. The previous
 * exporter therefore scrolled a laid-out but non-drawing WebView and produced
 * completely blank output. During export we keep the WebView VISIBLE with a
 * tiny alpha so its renderer and Canvas output remain active without showing a
 * disruptive preview flash to the user.
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
        val originalAlpha = preview.alpha
        val originalScrollY = preview.scrollY
        val originalTranslationX = preview.translationX

        // A VISIBLE WebView is required for reliable Canvas/WebView.draw capture.
        // Keep it practically transparent and move it outside the visible content
        // area only when it was not already the active preview.
        if (originalVisibility != View.VISIBLE) {
            preview.visibility = View.VISIBLE
            preview.alpha = 0.01f
            preview.translationX = preview.width.toFloat() + 2f
        }

        preview.post {
            val source = JSONObject.quote(markdown)
            preview.evaluateJavascript("renderMarkdown(" + source + ");") {
                waitForRenderedContent(
                    activity,
                    preview,
                    titleView?.text?.toString().orEmpty(),
                    originalVisibility,
                    originalAlpha,
                    originalScrollY,
                    originalTranslationX,
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
        preview: WebView,
        title: String,
        originalVisibility: Int,
        originalAlpha: Float,
        originalScrollY: Int,
        originalTranslationX: Float,
        attempt: Int
    ) {
        val script = "(function(){var root=document.getElementById('content')||document.body;var imgs=document.images||[];for(var i=0;i<imgs.length;i++){if(!imgs[i].complete||imgs[i].naturalWidth===0)return 'waiting';}return root&&root.scrollHeight>0?'ready':'waiting';})()"
        preview.evaluateJavascript(script, ValueCallback { value ->
            val ready = value != null && value.contains("ready")
            if (ready || attempt >= MAX_RENDER_WAIT_ATTEMPTS) {
                val safeTitle = if (title.isEmpty()) "Markdown" else title
                val restorePreview = {
                    if (!activity.isFinishing) {
                        preview.scrollTo(0, originalScrollY)
                        preview.translationX = originalTranslationX
                        preview.alpha = originalAlpha
                        preview.visibility = originalVisibility
                    }
                }
                val shown = RenderedMarkdownShare.showShareOptions(
                    activity,
                    preview,
                    safeTitle,
                    restorePreview
                )
                if (!shown) {
                    restorePreview.invoke()
                    Toast.makeText(activity, "内容仍在渲染，请稍后重试", Toast.LENGTH_SHORT).show()
                }
            } else {
                handler.postDelayed({
                    waitForRenderedContent(
                        activity,
                        preview,
                        title,
                        originalVisibility,
                        originalAlpha,
                        originalScrollY,
                        originalTranslationX,
                        attempt + 1
                    )
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
        private const val MAX_RENDER_WAIT_ATTEMPTS = 50
    }
}
