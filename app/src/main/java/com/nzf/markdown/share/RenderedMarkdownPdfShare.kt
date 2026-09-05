package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Picture
import android.support.v4.content.FileProvider
import android.webkit.WebView
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.io.IOException
import java.lang.Math

/**
 * Streams the complete rendered Markdown picture into a multi-page PDF.
 *
 * Each PDF page is produced from a slice of the full WebView picture. This
 * avoids WebView.draw() viewport clipping, so text and images below the screen
 * remain part of the exported document while memory stays bounded.
 */
object RenderedMarkdownPdfShare {
    private val PAGE_SIZE = PDRectangle.A4
    private const val PAGE_MARGIN = 18f

    fun createPdf(context: Context, webView: WebView, title: String): File? {
        val picture = RenderedMarkdownShare.captureRenderedPicture(webView) ?: return null
        return createPdf(context, picture, title)
    }

    fun createPdf(context: Context, picture: Picture, title: String): File? {
        val width = picture.width
        val contentHeight = picture.height
        if (width <= 0 || contentHeight <= 0) return null

        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists() && !directory.mkdirs()) return null
        val output = File(directory, "markdown_${System.currentTimeMillis()}.pdf")

        var document: PDDocument? = null
        try {
            document = PDDocument()
            val drawableWidth = PAGE_SIZE.width - PAGE_MARGIN * 2f
            val drawableHeight = PAGE_SIZE.height - PAGE_MARGIN * 2f
            val sourcePageHeight = Math.max(
                1.0,
                Math.floor((width.toDouble() * drawableHeight.toDouble()) / drawableWidth.toDouble())
            ).toInt()

            var offsetY = 0
            while (offsetY < contentHeight) {
                val sliceHeight = Math.min(sourcePageHeight, contentHeight - offsetY)
                val bitmap = Bitmap.createBitmap(width, sliceHeight, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.save()
                    canvas.translate(0f, -offsetY.toFloat())
                    picture.draw(canvas)
                    canvas.restore()

                    val page = PDPage(PAGE_SIZE)
                    document.addPage(page)
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    val renderedHeight = drawableWidth * sliceHeight.toFloat() / width.toFloat()
                    val contentStream = PDPageContentStream(document, page)
                    try {
                        contentStream.drawImage(
                            image,
                            PAGE_MARGIN,
                            PAGE_SIZE.height - PAGE_MARGIN - renderedHeight,
                            drawableWidth,
                            renderedHeight
                        )
                    } finally {
                        contentStream.close()
                    }
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                offsetY += sliceHeight
            }

            document.save(output)
            return output
        } catch (_: OutOfMemoryError) {
            output.delete()
            return null
        } catch (_: IOException) {
            output.delete()
            return null
        } finally {
            try {
                document?.close()
            } catch (_: IOException) {
            }
        }
    }

    fun shareRenderedWebView(context: Context, webView: WebView, title: String): Boolean {
        val picture = RenderedMarkdownShare.captureRenderedPicture(webView) ?: return false
        return shareRenderedPicture(context, picture, title)
    }

    fun shareRenderedPicture(context: Context, picture: Picture, title: String): Boolean {
        val pdf = createPdf(context, picture, title) ?: return false
        return try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                pdf
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, title, uri)
            }
            context.startActivity(Intent.createChooser(intent, "分享 PDF"))
            true
        } catch (_: Exception) {
            false
        }
    }
}
