package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Picture
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream

/**
 * Shares the complete rendered WebView document.
 *
 * WebView.draw() only proved reliable for the visible viewport on real devices.
 * Export therefore captures WebView's recorded document picture first and draws
 * that complete picture into either an image or a paginated PDF.
 */
object RenderedMarkdownShare {
    private const val MAX_IMAGE_HEIGHT = 12_000
    private const val MAX_IMAGE_MEMORY_BYTES = 48L * 1024L * 1024L

    fun showShareOptions(context: Context, webView: WebView, title: String, onFinished: (() -> Unit)? = null): Boolean {
        val picture = captureRenderedPicture(webView)
        if (picture == null) return false
        if (canShareAsImage(picture.width, picture.height)) {
            AlertDialog.Builder(context)
                .setTitle("选择分享格式")
                .setItems(arrayOf("图片", "PDF")) { _, which ->
                    if (which == 0) shareRenderedPictureAsImage(context, picture, title, onFinished)
                    else RenderedMarkdownPdfShare.shareRenderedPicture(context, picture, title, onFinished)
                }
                .setOnCancelListener { onFinished?.invoke() }
                .show()
        } else {
            android.widget.Toast.makeText(context, "当前内容较长，已使用 PDF 分享以保证完整内容", android.widget.Toast.LENGTH_LONG).show()
            RenderedMarkdownPdfShare.shareRenderedPicture(context, picture, title, onFinished)
        }
        return true
    }

    @Suppress("DEPRECATION")
    fun captureRenderedPicture(webView: WebView): Picture? {
        return try {
            val picture = webView.capturePicture()
            if (picture.width > 0 && picture.height > 0) picture else null
        } catch (_: Exception) {
            null
        }
    }

    fun getContentHeight(webView: WebView): Int {
        val picture = captureRenderedPicture(webView)
        if (picture != null) return picture.height
        return (webView.contentHeight.toFloat() * webView.scale).toInt()
    }

    private fun canShareAsImage(width: Int, contentHeight: Int): Boolean {
        if (width <= 0 || contentHeight <= 0 || contentHeight > MAX_IMAGE_HEIGHT) return false
        val estimatedBytes = width.toLong() * contentHeight.toLong() * 4L
        return estimatedBytes > 0L && estimatedBytes <= MAX_IMAGE_MEMORY_BYTES
    }

    private fun shareRenderedPictureAsImage(context: Context, picture: Picture, title: String, onFinished: (() -> Unit)?) {
        if (!canShareAsImage(picture.width, picture.height)) {
            RenderedMarkdownPdfShare.shareRenderedPicture(context, picture, title, onFinished)
            return
        }

        val bitmap = try {
            Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            RenderedMarkdownPdfShare.shareRenderedPicture(context, picture, title, onFinished)
            return
        }

        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            picture.draw(canvas)
            shareBitmap(context, bitmap, title)
        } catch (_: OutOfMemoryError) {
            RenderedMarkdownPdfShare.shareRenderedPicture(context, picture, title, onFinished)
            return
        } finally {
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
