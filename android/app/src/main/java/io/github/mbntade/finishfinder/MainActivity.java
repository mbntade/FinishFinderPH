package io.github.mbntade.finishfinder;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Finish Finder PH for Android.
 * Shows the same app as the website (bundled from /web) and adds phone features
 * the page calls through window.AndroidApp: open links, copy, share, save image, save backup.
 */
public class MainActivity extends ComponentActivity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String START = "https://" + HOST + "/assets/index.html";

    private WebView web;
    private ValueCallback<Uri[]> pendingChooser;
    private Uri cameraUri;
    private ActivityResultLauncher<Intent> chooserLauncher;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);

        // Keep the page clear of the status bar, navigation bar and keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setBuiltInZoomControls(false);

        // Serve the bundled files from a proper https address so saved projects (IndexedDB) work.
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .setDomain(HOST)
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                openExternal(u);
                return true;
            }
        });

        chooserLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (pendingChooser == null) return;
                    Uri[] picked = null;
                    Intent data = result.getData();
                    if (result.getResultCode() == RESULT_OK) {
                        if (data != null && data.getData() != null) {
                            picked = new Uri[]{data.getData()};
                        } else if (data != null && data.getClipData() != null && data.getClipData().getItemCount() > 0) {
                            picked = new Uri[]{data.getClipData().getItemAt(0).getUri()};
                        } else if (cameraUri != null && cameraFileHasPhoto()) {
                            picked = new Uri[]{cameraUri};
                        }
                    }
                    pendingChooser.onReceiveValue(picked);
                    pendingChooser = null;
                });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (pendingChooser != null) pendingChooser.onReceiveValue(null);
                pendingChooser = callback;
                try {
                    Intent pick = params.createIntent();
                    Intent chooser = Intent.createChooser(pick, "Choose a photo or file");
                    if (acceptsImages(params)) {
                        Intent camera = cameraIntent();
                        if (camera != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{camera});
                    }
                    chooserLauncher.launch(chooser);
                    return true;
                } catch (Exception e) {
                    pendingChooser = null;
                    return false;
                }
            }
        });

        web.addJavascriptInterface(new Bridge(), "AndroidApp");

        // Back button: let the page close forms / go back to the project list first.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                web.evaluateJavascript(
                        "(window.onAndroidBack && window.onAndroidBack()) ? '1' : '0'",
                        value -> { if (value == null || !value.contains("1")) finish(); });
            }
        });

        if (savedInstanceState != null) web.restoreState(savedInstanceState);
        else web.loadUrl(START);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /* ---------- photo picker helpers ---------- */

    private boolean acceptsImages(WebChromeClient.FileChooserParams params) {
        String[] types = params.getAcceptTypes();
        if (types == null) return false;
        for (String t : types) if (t != null && t.startsWith("image")) return true;
        return false;
    }

    private Intent cameraIntent() {
        try {
            File dir = new File(getCacheDir(), "camera");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File f = new File(dir, "photo-" + System.currentTimeMillis() + ".jpg");
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".files", f);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return i;
        } catch (Exception e) {
            cameraUri = null;
            return null;
        }
    }

    private boolean cameraFileHasPhoto() {
        try (java.io.InputStream in = getContentResolver().openInputStream(cameraUri)) {
            return in != null && in.read() != -1;
        } catch (Exception e) {
            return false;
        }
    }

    /* ---------- shared helpers ---------- */

    private void openExternal(Uri u) {
        runOnUiThread(() -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception e) {
                Toast.makeText(this, "No app found to open this link.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void share(Intent send) {
        runOnUiThread(() -> {
            try {
                startActivity(Intent.createChooser(send, "Share"));
            } catch (Exception e) {
                Toast.makeText(this, "Couldn't open sharing.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** Saves bytes to a public folder (Pictures/FinishFinder or Download/FinishFinder) and returns its address. */
    private String saveBytes(byte[] bytes, String name, String mime, boolean image) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        (image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS) + "/FinishFinder");
                Uri collection = image
                        ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                Uri uri = getContentResolver().insert(collection, v);
                if (uri == null) return null;
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) return null;
                    out.write(bytes);
                }
                return uri.toString();
            } else {
                // Android 9 and older: keep the file in the app's own folder and share it from there.
                File dir = new File(getExternalFilesDir(null),
                        (image ? "Pictures" : "Download") + "/FinishFinder");
                if (!dir.exists() && !dir.mkdirs()) return null;
                File f = new File(dir, name);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(bytes);
                }
                return FileProvider.getUriForFile(this, getPackageName() + ".files", f).toString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    /* ---------- functions the page can call as window.AndroidApp.* ---------- */

    private class Bridge {

        @JavascriptInterface
        public void openUrl(String url) {
            Uri u = Uri.parse(url);
            String scheme = u.getScheme();
            if ("https".equals(scheme) || "http".equals(scheme)) openExternal(u);
        }

        @JavascriptInterface
        public boolean copyText(String text) {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("Finish schedule", text));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void shareText(String text) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, text);
            share(i);
        }

        @JavascriptInterface
        public String saveImage(String base64, String name) {
            try {
                return saveBytes(Base64.decode(base64, Base64.DEFAULT), name, "image/jpeg", true);
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public void shareImage(String uri) {
            shareFile(uri, "image/jpeg");
        }

        @JavascriptInterface
        public String saveFile(String base64, String name, String mime) {
            try {
                return saveBytes(Base64.decode(base64, Base64.DEFAULT), name, mime, false);
            } catch (Exception e) {
                return null;
            }
        }

        @JavascriptInterface
        public void shareFile(String uri, String mime) {
            if (uri == null || uri.isEmpty()) return;
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(uri));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            share(i);
        }
    }
}
