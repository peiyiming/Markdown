package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.view.View
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream

/**
 * Shares the complete rendered WebView document.
 *
 * A WebView scroll screenshot is not reliable: WebView.draw() paints the
 * current viewport and can leave the off-screen area blank. For image export
 * we only allow documents that are safe to hold in one bitmap, temporarily lay
 * the WebView out to its full document height, and draw the complete document
 * in one pass. Tall documents are exported as PDF instead.
 */
object RenderedMarkdownShare {
    private const val MAX_IMAGE_HEIGHT = 12_000
    private const val MAX_IMAGE_MEMORY_BYTES = 48L * 1024L * 1024L

    fun showShareOptions(context: Context, webView: WebView, title: String, onFinished: (() -> Unit)? = null): Boolean {
        val width = webView.width
        val contentHeight = getContentHeight(webView)
        if (width <= 0 || contentHeight <= 0) return false
        if (canShareAsImage(width, contentHeight)) {
            AlertDialog.Builder(context)
                .setTitle("选择分享格式")
                .setItems(arrayOf("图片", "PDF")) { _, which ->
                    if (which == 0) shareRenderedWebViewAsImage(context, webView, title, onFinished)
                    else RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
                }
                .setOnCancelListener { onFinished?.invoke() }
                .show()
        } else {
            android.widget.Toast.makeText(context, "当前内容较长，已使用 PDF 分享以保证完整内容", android.widget.Toast.LENGTH_LONG).show()
            RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
        }
        return true
    }

    fun getContentHeight(webView: WebView): Int {
        return (webView.contentHeight.toFloat() * webView.scale).toInt()
    }

    private fun canShareAsImage(width: Int, contentHeight: Int): Boolean {
        if (contentHeight > MAX_IMAGE_HEIGHT) return false
        val estimatedBytes = width.toLong() * contentHeight.toLong() * 4L
        return estimatedBytes > 0L && estimatedBytes <= MAX_IMAGE_MEMORY_BYTES
    }

    private fun shareRenderedWebViewAsImage(context: Context, webView: WebView, title: String, onFinished: (() -> Unit)?) {
        val width = webView.width
        val contentHeight = getContentHeight(webView)
        if (!canShareAsImage(width, contentHeight)) {
            RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
            return
        }

        val bitmap = try {
            Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
            return
        }

        val originalScrollY = webView.scrollY
        val originalParams = webView.layoutParams
        val originalWidth = webView.width
        val originalHeight = webView.height

        try {
            // Layout the real WebView to the full rendered document before
            // drawing. This avoids stitching viewport snapshots, which was the
            // source of blank content below the first screen.
            val expandedParams = originalParams
            expandedParams.height = contentHeight
            webView.layoutParams = expandedParams
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(contentHeight, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, width, contentHeight)
            webView.scrollTo(0, 0)

            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            webView.draw(canvas)

            shareBitmap(context, bitmap, title)
        } catch (_: OutOfMemoryError) {
            RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
            return
        } catch (_: Exception) {
            RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
            return
        } finally {
            try {
                originalParams.height = originalHeight
                webView.layoutParams = originalParams
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(originalWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(originalHeight, View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, originalWidth, originalHeight)
                webView.scrollTo(0, originalScrollY)
                webView.requestLayout()
            } catch (_: Exception) {
            }
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        onFinished?.invoke()
    }

    private fun shareBitmap(context: Context, bitmap: Bitmap, title: String) {
        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Unable to create share cache directory")
        cleanupOldImages(directory)
        val file = File(directory, "markdown_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IllegalStateException("Unable to encode rendered Markdown image")
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
            if (file.isFile() && file.name.startsWith("markdown_") && file.name.endsWith(".png")) file.delete()
        }
    }
}
