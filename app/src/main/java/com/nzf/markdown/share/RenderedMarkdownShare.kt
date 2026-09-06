package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * Rendered Markdown sharing entry point.
 *
 * Image export is deliberately limited to content that fits inside the actual
 * WebView viewport. Android's WebView does not provide a reliable API 19-safe
 * way to rasterize arbitrary off-screen scroll content, so longer documents are
 * routed to the native Print Framework PDF exporter instead of producing a
 * truncated or blank image.
 */
object RenderedMarkdownShare {
    private const val MAX_IMAGE_MEMORY_BYTES = 48L * 1024L * 1024L

    fun showShareOptions(
        context: Context,
        webView: WebView,
        title: String,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        val contentHeight = getContentHeight(webView)
        val width = webView.width
        if (width <= 0 || webView.height <= 0 || contentHeight <= 0) return false

        if (canShareAsImage(webView, width, contentHeight)) {
            AlertDialog.Builder(context)
                .setTitle("选择分享格式")
                .setItems(arrayOf("图片", "PDF")) { _, which ->
                    if (which == 0) {
                        shareViewportImage(context, webView, title, onFinished)
                    } else {
                        RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
                    }
                }
                .setOnCancelListener { onFinished?.invoke() }
                .show()
        } else {
            Toast.makeText(
                context,
                "当前内容超过图片安全导出范围，已使用 PDF 分享以保证完整内容",
                Toast.LENGTH_LONG
            ).show()
            RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
        }
        return true
    }

    fun getContentHeight(webView: WebView): Int {
        val scale = webView.scale
        val rawHeight = webView.contentHeight
        if (rawHeight <= 0 || scale <= 0f) return 0
        return Math.ceil(rawHeight.toDouble() * scale.toDouble()).toInt()
    }

    private fun canShareAsImage(webView: WebView, width: Int, contentHeight: Int): Boolean {
        // Only rasterize when the complete document already fits in the visible
        // WebView. This prevents the historical one-screen-plus-blank export bug.
        if (contentHeight > webView.height) return false
        val estimatedBytes = width.toLong() * webView.height.toLong() * 4L
        return estimatedBytes > 0L && estimatedBytes <= MAX_IMAGE_MEMORY_BYTES
    }

    private fun shareViewportImage(
        context: Context,
        webView: WebView,
        title: String,
        onFinished: (() -> Unit)?
    ) {
        val width = webView.width
        val height = webView.height
        if (width <= 0 || height <= 0) {
            onFinished?.invoke()
            Toast.makeText(context, "内容尚未完成渲染，无法生成图片", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            Toast.makeText(context, "内容过大，已建议使用 PDF 分享", Toast.LENGTH_LONG).show()
            RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
            return
        }

        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            webView.draw(canvas)
            shareBitmap(context, bitmap, title)
        } catch (exception: Exception) {
            Toast.makeText(
                context,
                "图片生成失败：" + (exception.message ?: "未知错误"),
                Toast.LENGTH_LONG
            ).show()
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            onFinished?.invoke()
        }
    }

    private fun shareBitmap(context: Context, bitmap: Bitmap, title: String) {
        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Unable to create share cache directory")
        }
        cleanupOldImages(directory)
        val file = File(directory, "markdown_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                throw IllegalStateException("Unable to encode rendered Markdown image")
            }
            output.flush()
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, title, uri)
        }
        context.startActivity(Intent.createChooser(intent, "分享图片"))
    }

    private fun cleanupOldImages(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile() && file.name.startsWith("markdown_") && file.name.endsWith(".png")) {
                file.delete()
            }
        }
    }
}
