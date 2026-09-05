package com.nzf.markdown.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.webkit.WebView
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Creates a shareable PNG from an already-rendered Markdown WebView.
 * The caller must invoke this only after the preview has finished rendering.
 */
object RenderedMarkdownShare {
    fun renderWebView(webView: WebView): Bitmap? {
        val width = webView.width
        if (width <= 0) return null

        val contentHeight = webView.contentHeight * webView.scale
        if (contentHeight <= 0f) return null

        val height = contentHeight.toInt()
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            webView.draw(canvas)
            bitmap
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    fun shareBitmap(context: Context, bitmap: Bitmap, title: String) {
        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists()) directory.mkdirs()
        directory.listFiles()?.forEach { file ->
            if (file.isFile() && file.name.startsWith("markdown_") && file.name.endsWith(".png")) {
                file.delete()
            }
        }

        val file = File(directory, "markdown_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.flush()
        }

        val uri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_TITLE, title)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享到"))
    }
}
