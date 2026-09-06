package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Picture
import android.graphics.pdf.PdfDocument
import android.support.v4.content.FileProvider
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a paginated PDF from the complete recorded WebView picture.
 *
 * The previous implementation drew WebView directly on each PDF page. On real
 * devices that only painted the current viewport, so every page after the first
 * screen was blank. A Picture records the complete rendered document and can be
 * translated across PDF pages without relying on WebView's viewport drawing.
 */
object RenderedMarkdownPdfShare {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val PAGE_MARGIN = 24

    fun shareRenderedWebView(
        context: Context,
        webView: android.webkit.WebView,
        title: String,
        onFinished: (() -> Unit)? = null
    ) {
        val picture = RenderedMarkdownShare.captureRenderedPicture(webView)
        if (picture == null) {
            Toast.makeText(context, "内容尚未完成渲染，无法生成 PDF", Toast.LENGTH_SHORT).show()
            onFinished?.invoke()
            return
        }
        shareRenderedPicture(context, picture, title, onFinished)
    }

    fun shareRenderedPicture(
        context: Context,
        picture: Picture,
        title: String,
        onFinished: (() -> Unit)? = null
    ) {
        val sourceWidth = picture.width
        val sourceHeight = picture.height
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
        val contentWidth = PAGE_WIDTH - PAGE_MARGIN * 2
        val contentHeight = PAGE_HEIGHT - PAGE_MARGIN * 2
        val scale = contentWidth.toFloat() / sourceWidth.toFloat()
        val pageSourceHeight = contentHeight.toFloat() / scale
        val pageCount = Math.max(
            1,
            Math.ceil(sourceHeight.toDouble() / pageSourceHeight.toDouble()).toInt()
        )
        var document: PdfDocument? = null

        try {
            val pdfDocument = PdfDocument()
            document = pdfDocument
            var pageIndex = 0
            while (pageIndex < pageCount) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    PAGE_WIDTH,
                    PAGE_HEIGHT,
                    pageIndex + 1
                ).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)
                canvas.save()
                canvas.translate(PAGE_MARGIN.toFloat(), PAGE_MARGIN.toFloat())
                canvas.scale(scale, scale)
                canvas.translate(0f, -pageIndex.toFloat() * pageSourceHeight)
                picture.draw(canvas)
                canvas.restore()
                pdfDocument.finishPage(page)
                pageIndex++
            }

            FileOutputStream(output).use { stream ->
                pdfDocument.writeTo(stream)
                stream.flush()
            }
            pdfDocument.close()
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
