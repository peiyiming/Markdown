package android.print;

/**
 * API 19 exposes LayoutResultCallback with a package-private constructor.
 * Keeping this bridge in android.print lets the app use WebView's print
 * adapter without raising the minimum SDK level.
 */
public class MarkdownLayoutResultCallback extends PrintDocumentAdapter.LayoutResultCallback {
    public interface Listener {
        void onFinished(PrintDocumentInfo info, boolean changed);
        void onFailed(CharSequence error);
        void onCancelled();
    }

    private final Listener listener;

    public MarkdownLayoutResultCallback(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onLayoutFinished(PrintDocumentInfo info, boolean changed) {
        if (listener != null) {
            listener.onFinished(info, changed);
        }
    }

    @Override
    public void onLayoutFailed(CharSequence error) {
        if (listener != null) {
            listener.onFailed(error);
        }
    }

    @Override
    public void onLayoutCancelled() {
        if (listener != null) {
            listener.onCancelled();
        }
    }
}
