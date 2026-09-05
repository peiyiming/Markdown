package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream

/**
 * Shares the actual rendered WebView content.
 *
 * WebView.capturePicture() is deprecated and can contain only the initially
 * recorded viewport. Long images then leave a blank tail in exported files.
 * This exporter scrolls through the real WebView and captures each visible
 * slice after it has been rendered, so content below a tall image is preserved.
 */
object RenderedMarkdownShare {
    private const val MAX_IMAGE_HEIGHT = 12_000
    private const val MAX_IMAGE_MEMORY_BYTES = 48L * 1024L * 1024L
    private const val SLICE_SETTLE_DELAY_MS = 80L

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
        val bitmap = try { Bitmap.createBitmap(width, contentHeight, Bitmap.Config.ARGB_8888) } catch (_: OutOfMemoryError) {
            RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
            return
        }
        val originalScrollY = webView.scrollY
        captureImageSlices(webView, bitmap, 0, originalScrollY, object : SliceCallback {
            override fun onComplete() {
                try {
                    shareBitmap(context, bitmap, title)
                } catch (_: Exception) {
                    RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
                    return
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                onFinished?.invoke()
            }
            override fun onError() {
                if (!bitmap.isRecycled) bitmap.recycle()
                webView.scrollTo(0, originalScrollY)
                RenderedMarkdownPdfShare.shareRenderedWebView(context, webView, title, onFinished)
            }
        })
    }

    fun captureImageSlices(webView: WebView, destination: Bitmap, nextOffset: Int, originalScrollY: Int, callback: SliceCallback) {
        val contentHeight = getContentHeight(webView)
        val viewportHeight = webView.height
        if (viewportHeight <= 0 || contentHeight <= 0) {
            callback.onError()
            return
        }
        if (nextOffset >= contentHeight) {
            webView.scrollTo(0, originalScrollY)
            callback.onComplete()
            return
        }
        webView.scrollTo(0, nextOffset)
        webView.postDelayed({
            try {
                val actualOffset = webView.scrollY
                val remaining = contentHeight - actualOffset
                val captureHeight = Math.min(viewportHeight, remaining)
                if (captureHeight <= 0) {
                    webView.scrollTo(0, originalScrollY)
                    callback.onComplete()
                    return@postDelayed
                }
                val slice = Bitmap.createBitmap(webView.width, captureHeight, Bitmap.Config.ARGB_8888)
                try {
                    val sliceCanvas = Canvas(slice)
                    sliceCanvas.drawColor(android.graphics.Color.WHITE)
                    webView.draw(sliceCanvas)
                    Canvas(destination).drawBitmap(slice, 0f, actualOffset.toFloat(), null)
                } finally {
                    if (!slice.isRecycled) slice.recycle()
                }
                val followingOffset = actualOffset + captureHeight
                if (followingOffset >= contentHeight) {
                    webView.scrollTo(0, originalScrollY)
                    callback.onComplete()
                } else captureImageSlices(webView, destination, followingOffset, originalScrollY, callback)
            } catch (_: OutOfMemoryError) {
                callback.onError()
            } catch (_: Exception) {
                callback.onError()
            }
        }, SLICE_SETTLE_DELAY_MS)
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

    interface SliceCallback {
        fun onComplete()
        fun onError()
    }
}
