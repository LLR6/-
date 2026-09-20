package com.lr.sitetracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService executor = Executors.newFixedThreadPool(5);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " LR-Site-Tracker/1.0");

        webView.addJavascriptInterface(new Bridge(), "Android");
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/index.html");
    }

    public class Bridge {
        @JavascriptInterface
        public void openSite(String domain) {
            final String safe = normalizeDomain(domain);
            if (safe == null) return;
            runOnUiThread(() -> {
                Intent i = new Intent(MainActivity.this, BrowserActivity.class);
                i.putExtra("url", "https://" + safe);
                startActivity(i);
            });
        }

        @JavascriptInterface
        public void checkSite(String domain, String requestId) {
            final String safe = normalizeDomain(domain);
            if (safe == null) return;
            executor.submit(() -> checkOne(safe, requestId));
        }
    }

    private String normalizeDomain(String raw) {
        if (raw == null) return null;
        String d = raw.trim().toLowerCase();
        d = d.replaceFirst("^https?://", "");
        int slash = d.indexOf('/');
        if (slash >= 0) d = d.substring(0, slash);
        if (!d.matches("^[a-z0-9.-]+(:[0-9]{1,5})?$")) return null;
        return d;
    }

    private void checkOne(String domain, String requestId) {
        long start = System.currentTimeMillis();
        int code = -1;
        String state = "offline";
        String proto = "https";
        try {
            code = ping("https://" + domain);
            state = code > 0 && code < 600 ? "online" : "offline";
        } catch (Exception first) {
            try {
                proto = "http";
                code = ping("http://" + domain);
                state = code > 0 && code < 600 ? "online" : "offline";
            } catch (Exception ignored) {
                state = "offline";
            }
        }
        long ms = System.currentTimeMillis() - start;
        final String js = "window.onSiteCheckResult(" +
                JSONObject.quote(requestId) + "," +
                JSONObject.quote(domain) + "," +
                JSONObject.quote(state) + "," +
                code + "," + ms + "," +
                JSONObject.quote(proto) + ");";
        runOnUiThread(() -> webView.evaluateJavascript(js, null));
    }

    private int ping(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 LR-Site-Tracker/1.0");
        c.setRequestProperty("Range", "bytes=0-0");
        int code = c.getResponseCode();
        c.disconnect();
        return code;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
