package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.net.Uri
import android.support.v4.content.FileProvider
import android.support.v7.app.AlertDialog
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream
import java.lang.Math

/**
 * Shares the complete rendered Markdown result rather than the raw source.
 *
 * A WebView's draw() method is clipped to the view's viewport, even when the
 * destination Canvas is taller. capturePicture() gives us the full rendered
 * document, so long images and text below the visible viewport are preserved.
 */
object RenderedMarkdownShare {
    private const val MAX_IMAGE_HEIGHT = 12_000
    private const val MAX_IMAGE_MEMORY_BYTES = 48L * 1024L * 1024L

    fun showShareOptions(
        context: Context,
        webView: WebView,
        title: String,
        onFinished: (() -> Unit)? = null
    ): Boolean {
        val picture = captureRenderedPicture(webView) ?: return false
        val width = picture.width
        val contentHeight = picture.height
        if (width <= 0 || contentHeight <= 0) return false

        if (canShareAsImage(width, contentHeight)) {
            val dialog = AlertDialog.Builder(context)
                .setTitle("选择分享格式")
                .setItems(arrayOf("图片", "PDF")) { _, which ->
                    try {
                        if (which == 0) {
                            if (!shareRenderedPictureAsImage(context, picture, title)) {
                                RenderedMarkdownPdfShare.shareRenderedPicture(context, picture, title)
                            }
                        } else {
                            RenderedMarkdownPdfShare.shareRenderedPicture(context, picture, title)
                        }
                    } finally {
                        onFinished?.invoke()
                    }
                }
                .setOnCancelListener { onFinished?.invoke() }
                .create()
            dialog.show()
        } else {
            try {
                RenderedMarkdownPdfShare.shareRenderedPicture(context, picture, title)
            } finally {
                onFinished?.invoke()
            }
        }
        return true
    }

    fun captureRenderedPicture(webView: WebView): Picture? {
        return try {
            val picture = webView.capturePicture()
            if (picture.width > 0 && picture.height > 0) picture else null
        } catch (_: Exception) {
            null
        }
    }

    private fun canShareAsImage(width: Int, contentHeight: Int): Boolean {
        if (contentHeight > MAX_IMAGE_HEIGHT) return false
        val estimatedBytes = width.toLong() * contentHeight.toLong() * 4L
        return estimatedBytes > 0L && estimatedBytes <= MAX_IMAGE_MEMORY_BYTES
    }

    private fun shareRenderedPictureAsImage(context: Context, picture: Picture, title: String): Boolean {
        val width = picture.width
        val height = picture.height
        if (!canShareAsImage(width, height)) return false

        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: OutOfMemoryError) {
            return false
        }

        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            picture.draw(canvas)
            shareBitmap(context, bitmap, title)
            true
        } catch (_: Exception) {
            false
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
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

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
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
