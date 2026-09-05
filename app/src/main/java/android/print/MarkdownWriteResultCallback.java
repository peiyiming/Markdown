package android.print;

/**
 * API 19 exposes WriteResultCallback with a package-private constructor.
 * See MarkdownLayoutResultCallback for the compatibility rationale.
 */
public class MarkdownWriteResultCallback extends PrintDocumentAdapter.WriteResultCallback {
    public interface Listener {
        void onFinished(PageRange[] pages);
        void onFailed(CharSequence error);
        void onCancelled();
    }

    private final Listener listener;

    public MarkdownWriteResultCallback(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void onWriteFinished(PageRange[] pages) {
        if (listener != null) {
            listener.onFinished(pages);
        }
    }

    @Override
    public void onWriteFailed(CharSequence error) {
        if (listener != null) {
            listener.onFailed(error);
        }
    }

    @Override
    public void onWriteCancelled() {
        if (listener != null) {
            listener.onCancelled();
        }
    }
}
