package com.nzf.markdown.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.support.v4.content.FileProvider
import android.webkit.WebView
import java.io.File

/**
 * Generates PDF directly from the complete rendered WebView document.
 *
 * WebView's print pipeline is document-aware and does not depend on manually
 * scrolling and drawing individual viewports. This is substantially more
 * reliable for very tall images and for content that follows those images.
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

        adapter.onLayout(
            null,
            attributes,
            null,
            object : PrintDocumentAdapter.LayoutResultCallback() {
                override fun onLayoutFinished(info: android.print.PrintDocumentInfo?, changed: Boolean) {
                    if (info == null || info.pageCount == 0) {
                        output.delete()
                        onFinished?.invoke()
                        return
                    }
                    writeDocument(context, adapter, output, title, onFinished)
                }

                override fun onLayoutFailed(error: CharSequence?) {
                    output.delete()
                    onFinished?.invoke()
                }

                override fun onLayoutCancelled() {
                    output.delete()
                    onFinished?.invoke()
                }
            },
            Bundle()
        )
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
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE
            )
        } catch (_: Exception) {
            output.delete()
            onFinished?.invoke()
            return
        }

        adapter.onWrite(
            arrayOf(PageRange.ALL_PAGES),
            descriptor,
            CancellationSignal(),
            object : PrintDocumentAdapter.WriteResultCallback() {
                override fun onWriteFinished(pages: Array<PageRange>) {
                    closeDescriptor(descriptor)
                    sharePdf(context, output, title)
                    onFinished?.invoke()
                }

                override fun onWriteFailed(error: CharSequence?) {
                    closeDescriptor(descriptor)
                    output.delete()
                    onFinished?.invoke()
                }

                override fun onWriteCancelled() {
                    closeDescriptor(descriptor)
                    output.delete()
                    onFinished?.invoke()
                }
            }
        )
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

    private fun closeDescriptor(descriptor: ParcelFileDescriptor) {
        try {
            descriptor.close()
        } catch (_: Exception) {
        }
    }

    private fun safeDocumentName(title: String): String {
        val normalized = title.trim().ifEmpty { "Markdown" }
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
