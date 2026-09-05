package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.support.v4.content.FileProvider
import android.webkit.WebView
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil
import kotlin.math.min

/**
 * Creates shareable images from an already-rendered Markdown WebView.
 *
 * Long documents are rendered as multiple consecutive PNG slices instead of
 * allocating one giant Bitmap. This keeps memory usage bounded and avoids
 * Android's maximum Bitmap dimension limits while preserving the rendered
 * Markdown result exactly as it appears in the preview.
 */
object RenderedMarkdownShare {
    private const val MAX_SLICE_HEIGHT = 12_000

    fun renderWebView(webView: WebView): List<Bitmap>? {
        val width = webView.width
        if (width <= 0) return null

        val contentHeight = ceil(webView.contentHeight * webView.scale).toInt()
        if (contentHeight <= 0) return null

        val slices = ArrayList<Bitmap>()
        return try {
            var offsetY = 0
            while (offsetY < contentHeight) {
                val sliceHeight = min(MAX_SLICE_HEIGHT, contentHeight - offsetY)
                val bitmap = Bitmap.createBitmap(width, sliceHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.save()
                canvas.translate(0f, -offsetY.toFloat())
                webView.draw(canvas)
                canvas.restore()
                slices.add(bitmap)
                offsetY += sliceHeight
            }
            slices
        } catch (_: OutOfMemoryError) {
            slices.forEach { it.recycle() }
            null
        }
    }

    fun shareRenderedWebView(context: Context, webView: WebView, title: String): Boolean {
        val bitmaps = renderWebView(webView) ?: return false
        return try {
            shareBitmaps(context, bitmaps, title)
            true
        } catch (_: Exception) {
            false
        } finally {
            bitmaps.forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    fun shareBitmaps(context: Context, bitmaps: List<Bitmap>, title: String) {
        require(bitmaps.isNotEmpty())

        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists() && !directory.mkdirs()) {
            throw IllegalStateException("Unable to create share cache directory")
        }
        directory.listFiles()?.forEach { file ->
            if (file.isFile() && file.name.startsWith("markdown_") && file.name.endsWith(".png")) {
                file.delete()
            }
        }

        val uris = ArrayList<Uri>(bitmaps.size)
        bitmaps.forEachIndexed { index, bitmap ->
            val file = File(directory, "markdown_${System.currentTimeMillis()}_${index + 1}.png")
            FileOutputStream(file).use { output ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    throw IllegalStateException("Unable to encode rendered Markdown image")
                }
                output.flush()
            }
            uris.add(
                FileProvider.getUriForFile(
                    context,
                    context.packageName + ".fileprovider",
                    file
                )
            )
        }

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_TITLE, title)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            }
        }.apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, title, uris.first())
        }

        context.startActivity(Intent.createChooser(intent, "分享到"))
    }
}
