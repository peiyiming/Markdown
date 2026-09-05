package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.support.v4.content.FileProvider
import android.webkit.WebView
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.io.IOException

/**
 * Generates a PDF from the real rendered WebView, one visible slice at a time.
 * This intentionally does not use capturePicture(), because that API can leave
 * long-image and post-image content blank even when the DOM itself is complete.
 */
object RenderedMarkdownPdfShare {
    private val PAGE_SIZE = PDRectangle.A4
    private const val PAGE_MARGIN = 18f
    private const val SLICE_SETTLE_DELAY_MS = 80L

    fun shareRenderedWebView(
        context: Context,
        webView: WebView,
        title: String,
        onFinished: (() -> Unit)? = null
    ) {
        val width = webView.width
        val contentHeight = RenderedMarkdownShare.getContentHeight(webView)
        if (width <= 0 || contentHeight <= 0 || webView.height <= 0) {
            onFinished?.invoke()
            return
        }

        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists() && !directory.mkdirs()) {
            onFinished?.invoke()
            return
        }
        val output = File(directory, "markdown_${System.currentTimeMillis()}.pdf")
        val originalScrollY = webView.scrollY
        val document = PDDocument()

        capturePdfSlice(
            context,
            webView,
            title,
            document,
            output,
            originalScrollY,
            0,
            onFinished
        )
    }

    private fun capturePdfSlice(
        context: Context,
        webView: WebView,
        title: String,
        document: PDDocument,
        output: File,
        originalScrollY: Int,
        requestedOffset: Int,
        onFinished: (() -> Unit)?
    ) {
        val contentHeight = RenderedMarkdownShare.getContentHeight(webView)
        if (requestedOffset >= contentHeight) {
            finishAndShare(context, webView, title, document, output, originalScrollY, onFinished)
            return
        }

        webView.scrollTo(0, requestedOffset)
        webView.postDelayed({
            try {
                val actualOffset = webView.scrollY
                val remaining = contentHeight - actualOffset
                val sliceHeight = Math.min(webView.height, remaining)
                if (sliceHeight <= 0) {
                    finishAndShare(context, webView, title, document, output, originalScrollY, onFinished)
                    return@postDelayed
                }

                val bitmap = Bitmap.createBitmap(webView.width, sliceHeight, Bitmap.Config.ARGB_8888)
                try {
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    webView.draw(canvas)
                    appendPage(document, bitmap)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }

                val nextOffset = actualOffset + sliceHeight
                if (nextOffset >= contentHeight) {
                    finishAndShare(context, webView, title, document, output, originalScrollY, onFinished)
                } else {
                    capturePdfSlice(
                        context,
                        webView,
                        title,
                        document,
                        output,
                        originalScrollY,
                        nextOffset,
                        onFinished
                    )
                }
            } catch (_: OutOfMemoryError) {
                fail(document, output, webView, originalScrollY, onFinished)
            } catch (_: Exception) {
                fail(document, output, webView, originalScrollY, onFinished)
            }
        }, SLICE_SETTLE_DELAY_MS)
    }

    private fun appendPage(document: PDDocument, bitmap: Bitmap) {
        val page = PDPage(PAGE_SIZE)
        document.addPage(page)
        val drawableWidth = PAGE_SIZE.width - PAGE_MARGIN * 2f
        val drawableHeight = PAGE_SIZE.height - PAGE_MARGIN * 2f
        val scale = Math.min(drawableWidth / bitmap.width.toFloat(), drawableHeight / bitmap.height.toFloat())
        val renderedWidth = bitmap.width * scale
        val renderedHeight = bitmap.height * scale
        val x = (PAGE_SIZE.width - renderedWidth) / 2f
        val y = PAGE_SIZE.height - PAGE_MARGIN - renderedHeight
        val image = LosslessFactory.createFromImage(document, bitmap)
        val stream = PDPageContentStream(document, page)
        try {
            stream.drawImage(image, x, y, renderedWidth, renderedHeight)
        } finally {
            stream.close()
        }
    }

    private fun finishAndShare(
        context: Context,
        webView: WebView,
        title: String,
        document: PDDocument,
        output: File,
        originalScrollY: Int,
        onFinished: (() -> Unit)?
    ) {
        try {
            document.save(output)
            document.close()
            webView.scrollTo(0, originalScrollY)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", output)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_TITLE, title)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(context.contentResolver, title, uri)
            }
            context.startActivity(Intent.createChooser(intent, "分享 PDF"))
        } catch (_: Exception) {
            output.delete()
        } finally {
            try {
                document.close()
            } catch (_: IOException) {
            }
            webView.scrollTo(0, originalScrollY)
            onFinished?.invoke()
        }
    }

    private fun fail(
        document: PDDocument,
        output: File,
        webView: WebView,
        originalScrollY: Int,
        onFinished: (() -> Unit)?
    ) {
        try {
            document.close()
        } catch (_: IOException) {
        }
        output.delete()
        webView.scrollTo(0, originalScrollY)
        onFinished?.invoke()
    }
}
