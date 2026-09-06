package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.print.PDFPrint
import android.support.v4.content.FileProvider
import android.webkit.WebView
import android.widget.Toast
import java.io.File

/**
 * Exports rendered Markdown through Android's native PrintDocumentAdapter.
 *
 * Do not draw WebView pages onto a Canvas here. Real devices only guaranteed
 * viewport rendering for that approach. The Print Framework owns pagination and
 * receives the complete WebView document instead.
 */
object RenderedMarkdownPdfShare {
    fun shareRenderedWebView(
        context: Context,
        webView: WebView,
        title: String,
        onFinished: (() -> Unit)? = null
    ) {
        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists() && !directory.mkdirs()) {
            Toast.makeText(context, "无法创建 PDF 文件", Toast.LENGTH_SHORT).show()
            onFinished?.invoke()
            return
        }

        cleanupOldPdf(directory)
        val output = File(directory, "markdown_${System.currentTimeMillis()}.pdf")
        val mainHandler = Handler(Looper.getMainLooper())

        try {
            PDFPrint.generatePDFFromWebView(
                output,
                webView,
                object : PDFPrint.OnPDFPrintListener {
                    override fun onSuccess(file: File) {
                        mainHandler.post {
                            try {
                                sharePdf(context, file, title)
                            } catch (_: Exception) {
                                file.delete()
                                Toast.makeText(context, "无法打开 PDF 分享面板", Toast.LENGTH_SHORT).show()
                            } finally {
                                onFinished?.invoke()
                            }
                        }
                    }

                    override fun onError(exception: Exception) {
                        mainHandler.post {
                            output.delete()
                            Toast.makeText(
                                context,
                                "PDF 生成失败：" + (exception.message ?: "未知错误"),
                                Toast.LENGTH_LONG
                            ).show()
                            onFinished?.invoke()
                        }
                    }
                }
            )
        } catch (exception: Exception) {
            output.delete()
            Toast.makeText(
                context,
                "PDF 生成失败：" + (exception.message ?: "未知错误"),
                Toast.LENGTH_LONG
            ).show()
            onFinished?.invoke()
        }
    }

    private fun sharePdf(context: Context, output: File, title: String) {
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
    }

    private fun cleanupOldPdf(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile() && file.name.startsWith("markdown_") && file.name.endsWith(".pdf")) {
                file.delete()
            }
        }
    }
}
