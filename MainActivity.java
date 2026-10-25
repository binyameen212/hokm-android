package com.yameen.hokm;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class MainActivity extends Activity {
    private static final String APP_ORIGIN = "https://appassets.androidplatform.net";
    private static final int REQ_MIC = 1;

    private WebView webView;
    private PermissionRequest pendingMic;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(false); // lets other players' voices play

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleMicRequest(request);
                    }
                });
            }

            @Override
            public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingMic == request) pendingMic = null;
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serveBundledAsset(request.getUrl());
            }
        });

        setContentView(webView);
        webView.loadUrl(APP_ORIGIN + "/assets/index.html");
    }

    // Only our own bundled page may use the microphone, and only audio.
    private void handleMicRequest(PermissionRequest request) {
        Uri o = request.getOrigin();
        String origin = o.getScheme() + "://" + o.getHost();
        boolean wantsAudio = false;
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsAudio = true;
        }
        if (!APP_ORIGIN.equals(origin) || !wantsAudio) {
            request.deny();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            if (pendingMic != null) pendingMic.deny();
            pendingMic = request;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_MIC || pendingMic == null) return;
        PermissionRequest r = pendingMic;
        pendingMic = null;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            r.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
        } else {
            r.deny();
        }
    }

    private WebResourceResponse serveBundledAsset(Uri uri) {
        String origin = uri.getScheme() + "://" + uri.getHost();
        if (!APP_ORIGIN.equals(origin)) return null;
        String path = uri.getPath();
        if (path != null && path.equals("/assets/index.html")) {
            try {
                InputStream in = getAssets().open("index.html");
                return new WebResourceResponse("text/html", "UTF-8", 200, "OK",
                        Collections.singletonMap("Access-Control-Allow-Origin", "*"), in);
            } catch (IOException e) {
                // fall through to 404
            }
        }
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found",
                Collections.<String, String>emptyMap(), null);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
