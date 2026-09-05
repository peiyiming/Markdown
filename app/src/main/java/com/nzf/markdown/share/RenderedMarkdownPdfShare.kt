package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.MarkdownLayoutResultCallback
import android.print.MarkdownWriteResultCallback
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.support.v4.content.FileProvider
import android.webkit.WebView
import java.io.File

/**
 * Generates PDF directly from the complete rendered WebView document.
 *
 * The WebView print pipeline is document-aware, so PDF export does not depend
 * on manually drawing one viewport or creating one giant bitmap. This is the
 * preferred path for very tall images and content that follows them.
 */
object RenderedMarkdownPdfShare {

    fun shareRenderedWebView(
        context: Context,
        webView: WebView,
        title: String,
        onFinished: (() -> Unit)? = null
    ) {
        if (webView.width <= 0 || webView.height <= 0) {
            onFinished?.invoke()
            return
        }

        val directory = File(context.cacheDir, "markdown_share")
        if (!directory.exists() && !directory.mkdirs()) {
            onFinished?.invoke()
            return
        }

        cleanupOldPdf(directory)
        val output = File(directory, "markdown_${System.currentTimeMillis()}.pdf")
        val adapter = createPrintAdapter(webView, safeDocumentName(title))
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("markdown", "Markdown", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()

        try {
            adapter.onStart()
            adapter.onLayout(
                null,
                attributes,
                CancellationSignal(),
                MarkdownLayoutResultCallback(object : MarkdownLayoutResultCallback.Listener {
                    override fun onFinished(info: android.print.PrintDocumentInfo?, changed: Boolean) {
                        if (info == null || info.pageCount <= 0) {
                            finishWithCleanup(adapter, output, onFinished)
                            return
                        }
                        writeDocument(context, adapter, output, title, onFinished)
                    }

                    override fun onFailed(error: CharSequence?) {
                        finishWithCleanup(adapter, output, onFinished)
                    }

                    override fun onCancelled() {
                        finishWithCleanup(adapter, output, onFinished)
                    }
                }),
                Bundle()
            )
        } catch (_: Exception) {
            finishWithCleanup(adapter, output, onFinished)
        }
    }

    private fun createPrintAdapter(webView: WebView, documentName: String): PrintDocumentAdapter {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webView.createPrintDocumentAdapter(documentName)
        } else {
            @Suppress("DEPRECATION")
            webView.createPrintDocumentAdapter()
        }
    }

    private fun writeDocument(
        context: Context,
        adapter: PrintDocumentAdapter,
        output: File,
        title: String,
        onFinished: (() -> Unit)?
    ) {
        val descriptor = try {
            ParcelFileDescriptor.open(
                output,
                ParcelFileDescriptor.MODE_CREATE or
                    ParcelFileDescriptor.MODE_TRUNCATE or
                    ParcelFileDescriptor.MODE_READ_WRITE
            )
        } catch (_: Exception) {
            finishWithCleanup(adapter, output, onFinished)
            return
        }

        try {
            adapter.onWrite(
                arrayOf(PageRange.ALL_PAGES),
                descriptor,
                CancellationSignal(),
                MarkdownWriteResultCallback(object : MarkdownWriteResultCallback.Listener {
                    override fun onFinished(pages: Array<PageRange>?) {
                        closeDescriptor(descriptor)
                        try {
                            if (output.exists() && output.length() > 0L) {
                                sharePdf(context, output, title)
                            } else {
                                output.delete()
                            }
                        } finally {
                            finishAdapter(adapter, onFinished)
                        }
                    }

                    override fun onFailed(error: CharSequence?) {
                        closeDescriptor(descriptor)
                        finishWithCleanup(adapter, output, onFinished)
                    }

                    override fun onCancelled() {
                        closeDescriptor(descriptor)
                        finishWithCleanup(adapter, output, onFinished)
                    }
                })
            )
        } catch (_: Exception) {
            closeDescriptor(descriptor)
            finishWithCleanup(adapter, output, onFinished)
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
        }
    }

    private fun finishWithCleanup(
        adapter: PrintDocumentAdapter,
        output: File,
        onFinished: (() -> Unit)?
    ) {
        output.delete()
        finishAdapter(adapter, onFinished)
    }

    private fun finishAdapter(adapter: PrintDocumentAdapter, onFinished: (() -> Unit)?) {
        try {
            adapter.onFinish()
        } catch (_: Exception) {
        }
        onFinished?.invoke()
    }

    private fun closeDescriptor(descriptor: ParcelFileDescriptor) {
        try {
            descriptor.close()
        } catch (_: Exception) {
        }
    }

    private fun safeDocumentName(title: String): String {
        val trimmed = title.trim()
        val normalized = if (trimmed.isEmpty()) "Markdown" else trimmed
        return normalized.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    private fun cleanupOldPdf(directory: File) {
        directory.listFiles()?.forEach { file ->
            if (file.isFile() && file.name.startsWith("markdown_") && file.name.endsWith(".pdf")) {
                file.delete()
            }
        }
    }
}
