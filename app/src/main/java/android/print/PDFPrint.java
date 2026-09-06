package android.print;

import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.webkit.WebView;

import java.io.File;

/**
 * API 19 compatible bridge for exporting a fully rendered WebView through the
 * Android Print Framework. The callback classes intentionally live in the
 * android.print package because their constructors are package-visible on old
 * Android APIs.
 */
public final class PDFPrint {
    private PDFPrint() {
    }

    public interface OnPDFPrintListener {
        void onSuccess(File file);
        void onError(Exception exception);
    }

    public static void generatePDFFromWebView(
            final File file,
            final WebView webView,
            final OnPDFPrintListener listener
    ) {
        try {
            if (file.exists() && !file.delete()) {
                listener.onError(new Exception("Unable to replace existing PDF file"));
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                listener.onError(new Exception("Unable to create PDF directory"));
                return;
            }
            if (!file.createNewFile()) {
                listener.onError(new Exception("Unable to create PDF file"));
                return;
            }

            final PrintAttributes attributes = new PrintAttributes.Builder()
                    .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                    .setResolution(new PrintAttributes.Resolution("markdown", "markdown", 300, 300))
                    .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                    .build();

            final PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(file.getName());
            adapter.onLayout(
                    null,
                    attributes,
                    new CancellationSignal(),
                    new PrintDocumentAdapter.LayoutResultCallback() {
                        @Override
                        public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
                            writeAllPages(adapter, file, listener);
                        }

                        @Override
                        public void onLayoutFailed(CharSequence error) {
                            listener.onError(new Exception(error == null ? "PDF layout failed" : error.toString()));
                        }

                        @Override
                        public void onLayoutCancelled() {
                            listener.onError(new Exception("PDF layout cancelled"));
                        }
                    },
                    new Bundle()
            );
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    private static void writeAllPages(
            final PrintDocumentAdapter adapter,
            final File file,
            final OnPDFPrintListener listener
    ) {
        ParcelFileDescriptor destination = null;
        try {
            destination = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_WRITE | ParcelFileDescriptor.MODE_TRUNCATE
            );
            final ParcelFileDescriptor finalDestination = destination;
            adapter.onWrite(
                    new PageRange[]{PageRange.ALL_PAGES},
                    finalDestination,
                    new CancellationSignal(),
                    new PrintDocumentAdapter.WriteResultCallback() {
                        @Override
                        public void onWriteFinished(PageRange[] pages) {
                            closeQuietly(finalDestination);
                            if (file.exists() && file.length() > 0L) {
                                listener.onSuccess(file);
                            } else {
                                listener.onError(new Exception("Generated PDF is empty"));
                            }
                        }

                        @Override
                        public void onWriteFailed(CharSequence error) {
                            closeQuietly(finalDestination);
                            listener.onError(new Exception(error == null ? "PDF write failed" : error.toString()));
                        }

                        @Override
                        public void onWriteCancelled() {
                            closeQuietly(finalDestination);
                            listener.onError(new Exception("PDF write cancelled"));
                        }
                    }
            );
        } catch (Exception e) {
            closeQuietly(destination);
            listener.onError(e);
        }
    }

    private static void closeQuietly(ParcelFileDescriptor descriptor) {
        if (descriptor == null) return;
        try {
            descriptor.close();
        } catch (Exception ignored) {
        }
    }
}
