package com.lr.sitetracker;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;

public class BrowserActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private final Set<String> blockedHosts = new HashSet<>();
    private boolean adBlockEnabled = true;
    private Button adBlockButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra("url");
        if (url == null) url = "about:blank";

        SharedPreferences prefs = getSharedPreferences("lr_tracker", MODE_PRIVATE);
        adBlockEnabled = prefs.getBoolean("adblock_enabled", true);
        loadAdHosts();

        root = new FrameLayout(this);
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setBackgroundColor(Color.rgb(7,17,31));
        root.addView(shell, new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),dp(6),dp(8),dp(6));
        bar.setBackgroundColor(Color.rgb(11,28,47));

        Button back = button("‹");
        Button reload = button("↻");
        adBlockButton = button(adBlockEnabled ? "AD✓" : "AD×");
        adBlockButton.setTextSize(12);
        Button external = button("↗");

        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setText(Uri.parse(url).getHost());
        title.setPadding(dp(8),0,dp(8),0);

        bar.addView(back, new LinearLayout.LayoutParams(dp(48),dp(44)));
        bar.addView(reload, new LinearLayout.LayoutParams(dp(48),dp(44)));
        bar.addView(adBlockButton, new LinearLayout.LayoutParams(dp(58),dp(44)));
        bar.addView(title, new LinearLayout.LayoutParams(0,dp(44),1));
        bar.addView(external, new LinearLayout.LayoutParams(dp(48),dp(44)));
        shell.addView(bar, new LinearLayout.LayoutParams(-1,dp(56)));

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        shell.addView(progress, new LinearLayout.LayoutParams(-1,dp(3)));

        web = new WebView(this);
        shell.addView(web, new LinearLayout.LayoutParams(-1,0,1));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString() + " LR-Site-Tracker/2.2");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String target) {
                if (target == null) return true;
                if (target.startsWith("http://") || target.startsWith("https://")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target))); } catch (Exception ignored) {}
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (adBlockEnabled && request != null && request.getUrl() != null) {
                    String host = request.getUrl().getHost();
                    if (isBlockedHost(host)) return emptyResponse();
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (adBlockEnabled && url != null) {
                    try {
                        String host = Uri.parse(url).getHost();
                        if (isBlockedHost(host)) return emptyResponse();
                    } catch (Exception ignored) {}
                }
                return super.shouldInterceptRequest(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String target) {
                String host = Uri.parse(target).getHost();
                title.setText(host == null ? target : host);
                recordHistory(target, view.getTitle());
                if (adBlockEnabled) injectCosmeticFilters(view);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(-1,-1));
            }

            @Override
            public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                if (customCallback != null) customCallback.onCustomViewHidden();
                customCallback = null;
            }
        });

        final String initial = url;
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); else finish(); });
        reload.setOnClickListener(v -> web.reload());
        adBlockButton.setOnClickListener(v -> {
            adBlockEnabled = !adBlockEnabled;
            getSharedPreferences("lr_tracker", MODE_PRIVATE)
                    .edit()
                    .putBoolean("adblock_enabled", adBlockEnabled)
                    .apply();
            adBlockButton.setText(adBlockEnabled ? "AD✓" : "AD×");
            web.reload();
        });
        external.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl() == null ? initial : web.getUrl()))); }
            catch (Exception ignored) {}
        });

        web.loadUrl(url);
    }

    private void loadAdHosts() {
        blockedHosts.clear();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(getAssets().open("ad_hosts.txt")))) {
            String line;
            while ((line = br.readLine()) != null) {
                String h = line.trim().toLowerCase();
                if (h.isEmpty() || h.startsWith("#")) continue;
                int slash = h.indexOf('/');
                if (slash >= 0) h = h.substring(0, slash);
                if (h.startsWith("www.")) h = h.substring(4);
                if (!h.isEmpty()) blockedHosts.add(h);
            }
        } catch (Exception ignored) {}
    }

    private boolean isBlockedHost(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase();
        if (h.startsWith("www.")) h = h.substring(4);

        for (String blocked : blockedHosts) {
            if (h.equals(blocked) || h.endsWith("." + blocked)) return true;
        }

        String[] heuristic = {
                "doubleclick", "googlesyndication", "googleadservices",
                "exoclick", "trafficjunky", "juicyads", "popads",
                "popcash", "adsterra", "propellerads", "hilltopads",
                "clickadu", "clickadilla", "admaven", "onclickads",
                "tsyndicate", "twinred", "trafficstars", "adspyglass"
        };
        for (String k : heuristic) if (h.contains(k)) return true;
        return false;
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse(
                "text/plain",
                "utf-8",
                new ByteArrayInputStream(new byte[0])
        );
    }

    private void injectCosmeticFilters(WebView view) {
        String js = "(function(){" +
                "try{" +
                "var id='lr-adblock-style';" +
                "if(!document.getElementById(id)){" +
                "var s=document.createElement('style');s.id=id;" +
                "s.textContent='" +
                ".adsbygoogle,.ad,.ads,.advert,.advertisement,.ad-container,.ad-wrapper," +
                "[data-ad],[data-ads],[id^=ad_],[id^=ad-],[class^=ad_],[class^=ad-]," +
                "iframe[src*=doubleclick],iframe[src*=exoclick],iframe[src*=adsterra]," +
                "iframe[src*=popads],iframe[src*=juicyads],iframe[src*=trafficjunky]" +
                "{display:none!important;visibility:hidden!important;max-height:0!important;min-height:0!important;height:0!important;overflow:hidden!important}'" +
                ";(document.head||document.documentElement).appendChild(s);" +
                "}" +
                "try{window.open=function(){return null;};}catch(e){}" +
                "document.querySelectorAll('a[target=_blank]').forEach(function(a){a.target='_self';});" +
                "}catch(e){}" +
                "})();";
        view.evaluateJavascript(js, null);
    }

    private void recordHistory(String url, String pageTitle) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        try {
            SharedPreferences p = getSharedPreferences("lr_tracker", MODE_PRIVATE);
            JSONArray old = new JSONArray(p.getString("history_json", "[]"));
            JSONArray fresh = new JSONArray();
            JSONObject now = new JSONObject();
            now.put("url", url);
            now.put("title", pageTitle == null ? "" : pageTitle);
            now.put("time", System.currentTimeMillis());
            fresh.put(now);

            for (int i = 0; i < old.length() && fresh.length() < 200; i++) {
                JSONObject item = old.optJSONObject(i);
                if (item == null) continue;
                if (url.equals(item.optString("url"))) continue;
                fresh.put(item);
            }
            p.edit().putString("history_json", fresh.toString()).apply();
        } catch (Exception ignored) {}
    }

    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(20);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }

    @Override
    public void onBackPressed() {
        if (customView != null && customCallback != null) {
            customCallback.onCustomViewHidden();
            return;
        }
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
