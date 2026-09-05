package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.support.v4.content.FileProvider
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import kotlin.math.ceil

/**
 * Generates a paginated PDF from the complete rendered WebView document.
 *
 * The previous implementation manually drove PrintDocumentAdapter callbacks.
 * On API 19 those callbacks have package-private constructors; a compile-time
 * bridge can still fail at runtime on real devices, which resulted in a PDF
 * action that appeared to do nothing. This implementation uses only public
 * API 19 APIs and writes a PdfDocument directly.
 */
object RenderedMarkdownPdfShare {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 24

    fun shareRenderedWebView(
        context: Context,
        webView: WebView,
        title: String,
        onFinished: (() -> Unit)? = null
    ) {
        val sourceWidth = webView.width
        val sourceHeight = RenderedMarkdownShare.getContentHeight(webView)
        if (sourceWidth <= 0 || sourceHeight <= 0) {
            Toast.makeText(context, "内容尚未完成渲染，无法生成 PDF", Toast.LENGTH_SHORT).show()
            onFinished?.invoke()
            return
        }

        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(context, "无法创建 PDF 文件", Toast.LENGTH_SHORT).show()
            onFinished?.invoke()
            return
        }

        cleanupOldPdf(directory)
        val output = File(directory, "markdown_${System.currentTimeMillis()}.pdf")
        val originalScrollY = webView.scrollY
        val originalParams = webView.layoutParams
        val originalWidth = webView.width
        val originalHeight = webView.height
        var document: PdfDocument? = null

        try {
            // Make the WebView represent the whole document before drawing it
            // into successive PDF pages. PdfDocument clips each page, so no
            // giant bitmap is required for long documents.
            originalParams.height = sourceHeight
            webView.layoutParams = originalParams
            webView.measure(
                View.MeasureSpec.makeMeasureSpec(sourceWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(sourceHeight, View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, sourceWidth, sourceHeight)
            webView.scrollTo(0, 0)

            val contentWidth = PAGE_WIDTH - PAGE_MARGIN * 2
            val contentHeight = PAGE_HEIGHT - PAGE_MARGIN * 2
            val scale = contentWidth.toFloat() / sourceWidth.toFloat()
            val scaledDocumentHeight = sourceHeight.toFloat() * scale
            val pageCount = Math.max(1, ceil(scaledDocumentHeight / contentHeight.toFloat()).toInt())

            document = PdfDocument()
            var pageIndex = 0
            while (pageIndex < pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.translate(PAGE_MARGIN.toFloat(), PAGE_MARGIN.toFloat() - pageIndex * contentHeight.toFloat())
                canvas.scale(scale, scale)
                webView.draw(canvas)
                canvas.restore()
                document.finishPage(page)
                pageIndex++
            }

            FileOutputStream(output).use { stream ->
                document.writeTo(stream)
                stream.flush()
            }
            document.close()
            document = null

            if (!output.exists() || output.length() <= 0L) {
                output.delete()
                Toast.makeText(context, "PDF 生成失败", Toast.LENGTH_SHORT).show()
            } else {
                sharePdf(context, output, title)
            }
        } catch (_: OutOfMemoryError) {
            output.delete()
            Toast.makeText(context, "文档过大，PDF 生成失败", Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            output.delete()
            Toast.makeText(context, "PDF 生成失败，请稍后重试", Toast.LENGTH_SHORT).show()
        } finally {
            try {
                document?.close()
            } catch (_: Exception) {
            }
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
            onFinished?.invoke()
        }
    }

    private fun sharePdf(context: Context, output: File, title: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                output
            )
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
            Toast.makeText(context, "无法打开 PDF 分享面板", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanupOldPdf(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile() && file.name.startsWith("markdown_") && file.name.endsWith(".pdf")) {
                file.delete()
            }
        }
    }
}
