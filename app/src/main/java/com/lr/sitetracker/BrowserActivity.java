package com.lr.sitetracker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BrowserActivity extends Activity {
    private WebView web;
    private FrameLayout root;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra("url");
        if (url == null) url = "about:blank";

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
        Button external = button("↗");
        TextView title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(13);
        title.setSingleLine(true);
        title.setText(Uri.parse(url).getHost());
        title.setPadding(dp(8),0,dp(8),0);

        bar.addView(back, new LinearLayout.LayoutParams(dp(48),dp(44)));
        bar.addView(reload, new LinearLayout.LayoutParams(dp(48),dp(44)));
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
        s.setUserAgentString(s.getUserAgentString() + " LR-Site-Tracker/1.0");

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String target) {
                if (target.startsWith("http://") || target.startsWith("https://")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(target))); } catch (Exception ignored) {}
                return true;
            }
            @Override public void onPageFinished(WebView view, String target) {
                title.setText(Uri.parse(target).getHost());
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (customView != null) { callback.onCustomViewHidden(); return; }
                customView = view;
                customCallback = callback;
                root.addView(view, new FrameLayout.LayoutParams(-1,-1));
            }
            @Override public void onHideCustomView() {
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
        external.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(web.getUrl() == null ? initial : web.getUrl()))); }
            catch (Exception ignored) {}
        });
        web.loadUrl(url);
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

    @Override public void onBackPressed() {
        if (customView != null && customCallback != null) {
            customCallback.onCustomViewHidden();
            return;
        }
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
