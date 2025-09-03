package com.librelynx.lite;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.app.ActivityCompat;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import android.content.ActivityNotFoundException;


public class MainActivity extends Activity {
    
    private List<WebView> tabs;
    private int activeTabIndex;
    private EditText urlEditText;
    private Button goButton, settingsButton;
    private ProgressBar progressBar;
    private SwipeRefreshLayout swipeRefreshLayout;
    private SharedPreferences prefs;
    private List<String> browserHistory = new ArrayList<>();
    private AlertDialog tabSwitchDialog;  // Added for improved tab management
    
    private String currentSearchEngine = "mojeek"; // Default remains mojeek
    private String currentTheme = "dark";
    
    private static final int PERMISSION_REQUEST_CODE = 1;
    private static final String PREFS_NAME = "LibreLynxLitePrefs";
    private static final String KEY_FIRST_LAUNCH = "first_launch";
    private static final String KEY_THEME = "theme";
    private static final int MAX_HISTORY_SIZE = 20;
    
    private static final String BRAVE_SEARCH = "https://search.brave.com/search?q=";
    private static final String MOJEEK_SEARCH = "https://www.mojeek.com/search?q=";
    
    // Bookmark helper class
    private static class BookmarkItem {
        private String title;
        private String url;

        public BookmarkItem(String title, String url) {
            this.title = title;
            this.url = url;
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
    }

    // TabItem class for improved tab management
    private static class TabItem {
        private String title;
        private String url;
        private int tabIndex;

        public TabItem(String title, String url, int tabIndex) {
            this.title = title != null ? title : "New Tab";
            this.url = url != null ? url : "";
            this.tabIndex = tabIndex;
        }

        public String getTitle() { return title; }
        public String getUrl() { return url; }
        public int getTabIndex() { return tabIndex; }
    }

    // Enhanced Bookmark Adapter with proper click handling
    private class BookmarkAdapter extends ArrayAdapter<BookmarkItem> {
        private List<BookmarkItem> bookmarks;
        private Context context;

        public BookmarkAdapter(Context context, List<BookmarkItem> bookmarks) {
            super(context, 0, bookmarks);
            this.context = context;
            this.bookmarks = bookmarks;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.bookmark_tile_layout, parent, false);
            }

            BookmarkItem bookmark = bookmarks.get(position);
            
            TextView titleView = convertView.findViewById(R.id.bookmark_title);
            TextView urlView = convertView.findViewById(R.id.bookmark_url);
            TextView faviconView = convertView.findViewById(R.id.bookmark_favicon);
            TextView menuView = convertView.findViewById(R.id.bookmark_menu);

            if (titleView == null || urlView == null || faviconView == null || menuView == null) {
                Log.e("BookmarkAdapter", "Failed to find views in bookmark_tile_layout.xml");
                Toast.makeText(context, "Error: Bookmark layout missing views", Toast.LENGTH_SHORT).show();
                return convertView;
            }

            titleView.setText(bookmark.getTitle());
            urlView.setText(bookmark.getUrl());
            
            // Set favicon based on domain
            String favicon = getFaviconForDomain(bookmark.getUrl());
            faviconView.setText(favicon);
            
            // Apply theme colors
            androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) convertView;
            int cardColor, textColor;
            
            switch (currentTheme) {
                case "ocean":
                    cardColor = ContextCompat.getColor(context, R.color.ocean_background_alt);
                    textColor = ContextCompat.getColor(context, R.color.ocean_text_color);
                    break;
                case "forest":
                    cardColor = ContextCompat.getColor(context, R.color.forest_background_alt);
                    textColor = ContextCompat.getColor(context, R.color.forest_text_color);
                    break;
                case "sunset":
                    cardColor = ContextCompat.getColor(context, R.color.sunset_background_alt);
                    textColor = ContextCompat.getColor(context, R.color.sunset_text_color);
                    break;
                case "dark":
                default:
                    cardColor = ContextCompat.getColor(context, R.color.dark_background_alt);
                    textColor = ContextCompat.getColor(context, R.color.dark_text_color);
                    break;
            }
            
            cardView.setCardBackgroundColor(cardColor);
            titleView.setTextColor(textColor);
            urlView.setTextColor(textColor);
            faviconView.setTextColor(textColor);
            menuView.setTextColor(textColor);
            
            // Make CardView properly handle clicks
            cardView.setClickable(true);
            cardView.setFocusable(true);
            cardView.setFocusableInTouchMode(true);
            
            // Handle main bookmark click - open the bookmark
            cardView.setOnClickListener(v -> {
                Log.d("BookmarkAdapter", "Bookmark clicked: " + bookmark.getUrl());
                try {
                    if (isValidUrl(bookmark.getUrl())) {
                        WebView webView = getActiveWebView();
                        if (webView != null) {
                            webView.loadUrl(bookmark.getUrl());
                            urlEditText.setText(bookmark.getUrl());
                            Log.d("BookmarkAdapter", "Loaded bookmark: " + bookmark.getUrl());
                            
                            // Close the bookmarks dialog after loading
                            if (context instanceof MainActivity) {
                                Toast.makeText(context, "Loading: " + bookmark.getTitle(), Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.e("BookmarkAdapter", "No active WebView");
                            Toast.makeText(context, "Error: No active tab", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        Log.w("BookmarkAdapter", "Invalid URL: " + bookmark.getUrl());
                        Toast.makeText(context, "Invalid bookmark URL", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Log.e("BookmarkAdapter", "Error loading bookmark: " + e.getMessage(), e);
                    Toast.makeText(context, "Error loading bookmark", Toast.LENGTH_SHORT).show();
                }
            });
            
            // Handle menu click - show options
            menuView.setOnClickListener(v -> {
                Log.d("BookmarkAdapter", "Menu clicked for bookmark: " + bookmark.getUrl());
                showBookmarkMenu(bookmark, position, v); // Pass the clicked view as anchor
            });
            
            // Log view creation for debugging
            Log.d("BookmarkAdapter", "Created view for bookmark: " + bookmark.getUrl());
            
            return convertView;
        }
        
        private String getFaviconForDomain(String url) {
            try {
                String domain = url.toLowerCase();
                if (domain.contains("google")) return "G";
                if (domain.contains("youtube")) return "▶";
                if (domain.contains("facebook")) return "F";
                if (domain.contains("twitter")) return "T";
                if (domain.contains("instagram")) return "I";
                if (domain.contains("reddit")) return "R";
                if (domain.contains("github")) return "G";
                if (domain.contains("stackoverflow")) return "S";
                if (domain.contains("wikipedia")) return "W";
                if (domain.contains("amazon")) return "A";
                if (domain.contains("netflix")) return "N";
                if (domain.contains("spotify")) return "♫";
                return "🌐";
            } catch (Exception e) {
                Log.e("BookmarkAdapter", "Error getting favicon for URL: " + url, e);
                return "🌐";
            }
        }
    }

    // TabAdapter class for improved tab display
    private class TabAdapter extends ArrayAdapter<TabItem> {
        private List<TabItem> tabItems;
        private Context context;

        public TabAdapter(Context context, List<TabItem> tabItems) {
            super(context, 0, tabItems);
            this.context = context;
            this.tabItems = tabItems;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.tab_item_layout, parent, false);
            }

            TabItem tabItem = tabItems.get(position);
            
            TextView titleView = convertView.findViewById(R.id.tab_title);
            TextView urlView = convertView.findViewById(R.id.tab_url);
            TextView closeButton = convertView.findViewById(R.id.tab_close);
            TextView activeIndicator = convertView.findViewById(R.id.tab_active_indicator);

            if (titleView != null) {
                titleView.setText(tabItem.getTitle());
            }
            if (urlView != null) {
                String displayUrl = tabItem.getUrl();
                if (displayUrl.length() > 50) {
                    displayUrl = displayUrl.substring(0, 47) + "...";
                }
                urlView.setText(displayUrl);
            }

            // Show active tab indicator
            if (activeIndicator != null) {
                activeIndicator.setVisibility(tabItem.getTabIndex() == activeTabIndex ? View.VISIBLE : View.GONE);
            }

            androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) convertView;
            int cardColor, textColor;
            
            // Apply theme colors
            switch (currentTheme) {
                case "ocean":
                    cardColor = ContextCompat.getColor(context, R.color.ocean_background_alt);
                    textColor = ContextCompat.getColor(context, R.color.ocean_text_color);
                    break;
                case "forest":
                    cardColor = ContextCompat.getColor(context, R.color.forest_background_alt);
                    textColor = ContextCompat.getColor(context, R.color.forest_text_color);
                    break;
                case "sunset":
                    cardColor = ContextCompat.getColor(context, R.color.sunset_background_alt);
                    textColor = ContextCompat.getColor(context, R.color.sunset_text_color);
                    break;
                case "dark":
                default:
                    cardColor = ContextCompat.getColor(context, R.color.dark_background_alt);
                    textColor = ContextCompat.getColor(context, R.color.dark_text_color);
                    break;
            }
            
            cardView.setCardBackgroundColor(cardColor);
            if (titleView != null) titleView.setTextColor(textColor);
            if (urlView != null) urlView.setTextColor(textColor);
            if (closeButton != null) closeButton.setTextColor(textColor);
            if (activeIndicator != null) activeIndicator.setTextColor(textColor);
            
            // Handle tab selection
            cardView.setOnClickListener(v -> {
                switchTab(tabItem.getTabIndex());
                // Dismiss the dialog
                if (tabSwitchDialog != null) {
                    tabSwitchDialog.dismiss();
                }
            });
            
            // Handle tab close
            if (closeButton != null) {
                closeButton.setOnClickListener(v -> {
                    closeTab(tabItem.getTabIndex());
                    // Refresh the dialog
                    showImprovedSwitchTabDialog();
                });
            }
            
            return convertView;
        }
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            Log.d("MainActivity", "Starting onCreate");
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            currentTheme = prefs.getString(KEY_THEME, "dark");
            switch (currentTheme) {
                case "ocean":
                    setTheme(R.style.AppTheme_Ocean);
                    break;
                case "forest":
                    setTheme(R.style.AppTheme_Forest);
                    break;
                case "sunset":
                    setTheme(R.style.AppTheme_Sunset);
                    break;
                case "dark":
                default:
                    setTheme(R.style.AppTheme_Dark);
                    break;
            }
            
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            
            Log.d("MainActivity", "Initializing views");
            initializeViews();
            Log.d("MainActivity", "Loading history");
            loadHistory();
            Log.d("MainActivity", "Setting up tabs");
            setupTabs();
            Log.d("MainActivity", "Setting up privacy settings");
            setupPrivacySettings();
            Log.d("MainActivity", "Setting up buttons");
            setupButtons();
            Log.d("MainActivity", "Setting up swipe refresh");
            setupSwipeRefresh();
            Log.d("MainActivity", "Checking permissions");
            checkPermissions();
            
            if (prefs.getBoolean(KEY_FIRST_LAUNCH, true)) {
                Log.d("MainActivity", "First launch, showing search engine dialog");
                showSearchEngineDialog();
            } else {
                Log.d("MainActivity", "Loading search engine");
                loadSearchEngine();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in onCreate: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing app: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void initializeViews() {
        try {
            urlEditText = findViewById(R.id.url_edittext);
            goButton = findViewById(R.id.go_button);
            settingsButton = findViewById(R.id.settings_button);
            progressBar = findViewById(R.id.progress_bar);
            swipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
            
            if (urlEditText == null || goButton == null || settingsButton == null || 
                progressBar == null || swipeRefreshLayout == null) {
                Log.e("MainActivity", "One or more views not found in activity_main.xml");
                Toast.makeText(this, "Error: UI components missing", Toast.LENGTH_LONG).show();
                return;
            }
            
            prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            currentSearchEngine = prefs.getString("search_engine", "mojeek");
            Log.d("MainActivity", "Views initialized, search engine: " + currentSearchEngine);
        } catch (Exception e) {
            Log.e("MainActivity", "Error in initializeViews: " + e.getMessage(), e);
            Toast.makeText(this, "Error initializing views", Toast.LENGTH_LONG).show();
        }
    }

    // Load history from SharedPreferences
    private void loadHistory() {
        try {
            SharedPreferences historyPrefs = getSharedPreferences("LibreLynxHistory", MODE_PRIVATE);
            String historyOrder = historyPrefs.getString("history_order", "");
            
            browserHistory.clear();
            if (!historyOrder.isEmpty()) {
                String[] urls = historyOrder.split("\\|");
                for (String url : urls) {
                    if (!url.isEmpty()) {
                        browserHistory.add(url);
                    }
                }
            }
            
            Log.d("History", "Loaded " + browserHistory.size() + " items from history");
        } catch (Exception e) {
            Log.e("History", "Error loading history: " + e.getMessage(), e);
        }
    }

    // Add URL to history
    private void addToHistory(String url, String title) {
        try {
            if (url == null || url.isEmpty() || url.equals("about:blank")) {
                return;
            }
            
            // Remove duplicate if exists
            browserHistory.remove(url);
            
            // Add to beginning of list
            browserHistory.add(0, url);
            
            // Limit history size
            if (browserHistory.size() > MAX_HISTORY_SIZE) {
                browserHistory = browserHistory.subList(0, MAX_HISTORY_SIZE);
            }
            
            // Save to SharedPreferences for persistence
            SharedPreferences historyPrefs = getSharedPreferences("LibreLynxHistory", MODE_PRIVATE);
            SharedPreferences.Editor editor = historyPrefs.edit();
            
            // Store URL with title
            if (title != null && !title.isEmpty()) {
                editor.putString(url, title);
            } else {
                editor.putString(url, url); // Use URL as title if no title available
            }
            
            // Store the ordered list
            StringBuilder historyString = new StringBuilder();
            for (int i = 0; i < browserHistory.size(); i++) {
                if (i > 0) historyString.append("|");
                historyString.append(browserHistory.get(i));
            }
            editor.putString("history_order", historyString.toString());
            editor.apply();
            
            Log.d("History", "Added to history: " + url + " with title: " + title);
        } catch (Exception e) {
            Log.e("History", "Error adding to history: " + e.getMessage(), e);
        }
    }
    
    private void setupTabs() {
        try {
            tabs = new ArrayList<>();
            activeTabIndex = 0;
            WebView webView = new WebView(this);
            setupWebView(webView);
            tabs.add(webView);
            ViewGroup webViewContainer = findViewById(R.id.webview);
            if (webViewContainer == null) {
                Log.e("MainActivity", "WebView container not found in activity_main.xml");
                Toast.makeText(this, "Error: WebView container missing", Toast.LENGTH_LONG).show();
                return;
            }
            webViewContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ));
            ((ViewGroup) webViewContainer).removeAllViews();
            ((ViewGroup) webViewContainer).addView(webView);
            Log.d("MainActivity", "Tabs set up, added WebView to container");
            webView.loadUrl("https://www.mojeek.com"); // Ensure initial load
        } catch (Exception e) {
            Log.e("MainActivity", "Error in setupTabs: " + e.getMessage(), e);
            Toast.makeText(this, "Error setting up tabs", Toast.LENGTH_LONG).show();
        }
    }
    
    private void setupPrivacySettings() {
        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(false);
            cookieManager.setAcceptThirdPartyCookies(getActiveWebView(), false);
            Log.d("MainActivity", "Privacy settings configured");
        } catch (Exception e) {
            Log.e("MainActivity", "Error in setupPrivacySettings: " + e.getMessage(), e);
        }
    }
    
    private void setupWebView(WebView webView) {
        try {
            WebSettings settings = webView.getSettings();
            
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            
            settings.setSavePassword(false);
            settings.setSaveFormData(false);
            settings.setGeolocationEnabled(false);
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            
            settings.setCacheMode(WebSettings.LOAD_DEFAULT);
            
            settings.setUserAgentString("Mozilla/5.0 (Android; Mobile; rv:68.0) LibreLynx/1.0");
            
            webView.setWebViewClient(new PrivacyWebViewClient());
            webView.setWebChromeClient(new WebChromeClient() {
                @Override
                public void onProgressChanged(WebView view, int newProgress) {
                    try {
                        progressBar.setProgress(newProgress);
                        swipeRefreshLayout.setEnabled(newProgress >= 100);
                        if (newProgress == 100) {
                            progressBar.setVisibility(View.GONE);
                            swipeRefreshLayout.setRefreshing(false);
                            Log.d("MainActivity", "Page load completed");
                        } else {
                            progressBar.setVisibility(View.VISIBLE);
                            Log.d("MainActivity", "Page loading progress: " + newProgress);
                        }
                    } catch (Exception e) {
                        Log.e("MainActivity", "Error in onProgressChanged: " + e.getMessage(), e);
                    }
                }
            });
            Log.d("MainActivity", "WebView configured");
        } catch (Exception e) {
            Log.e("MainActivity", "Error in setupWebView: " + e.getMessage(), e);
            Toast.makeText(this, "Error configuring WebView", Toast.LENGTH_LONG).show();
        }
    }
    
    private WebView getActiveWebView() {
        try {
            if (tabs == null || tabs.isEmpty() || activeTabIndex < 0 || activeTabIndex >= tabs.size()) {
                Log.e("MainActivity", "Invalid tab state: tabs=" + (tabs == null ? "null" : tabs.size()) + 
                      ", activeTabIndex=" + activeTabIndex);
                return null;
            }
            WebView webView = tabs.get(activeTabIndex);
            if (webView == null) {
                Log.e("MainActivity", "Active WebView is null at index: " + activeTabIndex);
            }
            return webView;
        } catch (Exception e) {
            Log.e("MainActivity", "Error in getActiveWebView: " + e.getMessage(), e);
            return null;
        }
    }
    
    // Updated CSS injection method for minimal search interface
public void injectMinimalCSS(WebView webView) {
    if (webView == null || webView.getUrl() == null) return;

    String url = webView.getUrl().toLowerCase();
    String css = createWorkingCSS(url);

    // Only inject CSS if we have rules for this site
    if (css.trim().isEmpty()) return;

    // Escape CSS for JS injection
    String escapedCSS = css.replace("\\", "\\\\")
                          .replace("'", "\\'")
                          .replace("\"", "\\\"")
                          .replace("\n", "\\n")
                          .replace("\r", "");

    // Simple CSS injection without DOM manipulation
    String jsCode =
        "(function(){" +
        "try {" +
            "var existingStyle = document.getElementById('librelynx-style');" +
            "if(existingStyle) existingStyle.remove();" +
            "var style = document.createElement('style');" +
            "style.id = 'librelynx-style';" +
            "style.innerHTML = '" + escapedCSS + "';" +
            "document.head.appendChild(style);" +
            "console.log('LibreLynx CSS applied successfully');" +
            "return 'success';" +
        "} catch(e) {" +
            "console.error('CSS injection failed:', e);" +
            "return 'error: ' + e.message;" +
        "}" +
        "})()";

    webView.evaluateJavascript(jsCode, result -> {
        Log.d("CSS Injection", "JavaScript result: " + result);
    });
}

private String createWorkingCSS(String url) {
    String css = "";

    if (url.contains("search.brave.com")) {
    css += "/* Brave Search minimal UI */" +
           ".header-button-icon.svelte-2b86ho { display: none !important; }" +  // hide settings cog
           ".download-cta { display: none !important; }" +                     // hide download CTA
           "footer.desktop-small-regular.t-tertiary { display: none !important; }" + // hide footer copyright
           ".desktop-small-regular.t-tertiary { display: none !important; }" +       // hide extra footer links
           ".content.svelte-6sdecn { display: none !important; }";            // hide Resources / Products / Policies
}

    if (url.contains("mojeek.com")) {
    css += "/* Mojeek homepage & search results clean-up */" +
           ".pop-in.js-pop { display: none !important; }" +
           "label.popover-label, label[for='settings'] { display: none !important; }" +
           ".hamburger-ctn { display: none !important; }" +
           ".bullets { display: none !important; }" +
           ".para { display: none !important; }" +
           ".footer.inverted { display: none !important; }" +
           ".feedback-sticky { display: none !important; }" +
           ".footer-loc { display: none !important; }" +
           ".footer { display: none !important; }" +
           ".scb { display: none !important; }"; 
}

    return css;
}
    // Helper method to check if URL is a search engine
    private boolean isSearchEngineUrl(String url) {
        return url != null && (
            url.contains("search.brave.com") ||
            url.contains("mojeek.com")
        );
    }
    
    private void setupSwipeRefresh() {
        try {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                Log.d("SwipeRefresh", "Pull-down refresh triggered");
                WebView activeWebView = getActiveWebView();
                if (activeWebView != null) {
                    activeWebView.reload();
                } else {
                    Log.e("SwipeRefresh", "No active WebView to reload");
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Error: No active tab", Toast.LENGTH_SHORT).show();
                }
            });
            int color1, color2;
            switch (currentTheme) {
                case "ocean":
                    color1 = R.color.ocean_swipe_color_1;
                    color2 = R.color.ocean_swipe_color_2;
                    break;
                case "forest":
                    color1 = R.color.forest_swipe_color_1;
                    color2 = R.color.forest_swipe_color_2;
                    break;
                case "sunset":
                    color1 = R.color.sunset_swipe_color_1;
                    color2 = R.color.sunset_swipe_color_2;
                    break;
                case "dark":
                default:
                    color1 = R.color.dark_swipe_color_1;
                    color2 = R.color.dark_swipe_color_2;
                    break;
            }
            swipeRefreshLayout.setColorSchemeResources(color1, color2);
            swipeRefreshLayout.setEnabled(true);
            swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> {
                WebView webView = getActiveWebView();
                return webView != null && webView.getScrollY() > 0;
            });
            Log.d("MainActivity", "Swipe refresh configured");
        } catch (Exception e) {
            Log.e("MainActivity", "Error in setupSwipeRefresh: " + e.getMessage(), e);
            Toast.makeText(this, "Error setting up swipe refresh", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void setupButtons() {
    try {
        goButton.setOnClickListener(v -> navigateToUrl());
        
        settingsButton.setOnClickListener(v -> {
            PopupMenu popupMenu = new PopupMenu(this, v);
            
            popupMenu.getMenu().add(0, 1, 0, "← Back");
            popupMenu.getMenu().add(0, 2, 1, "→ Forward");
            popupMenu.getMenu().add(0, 19, 2, "🔄 Refresh");
            popupMenu.getMenu().add(0, 3, 3, "────────────");
            popupMenu.getMenu().add(0, 16, 4, "🆕 Open New Tab");
            popupMenu.getMenu().add(0, 17, 5, "🔢 Tabs");
            popupMenu.getMenu().add(0, 18, 6, "────────────");
            popupMenu.getMenu().add(0, 6, 7, "⭐ Bookmark This Page");
            popupMenu.getMenu().add(0, 4, 8, "📚 Bookmarks");
            popupMenu.getMenu().add(0, 20, 9, "📖 History");
            popupMenu.getMenu().add(0, 21, 10, "🗑️ Clear All History"); // New menu item
            popupMenu.getMenu().add(0, 5, 11, "💾 Downloads");
            popupMenu.getMenu().add(0, 7, 12, "────────────");
            popupMenu.getMenu().add(0, 8, 13, R.string.search_engine_title);
            popupMenu.getMenu().add(0, 9, 14, "Choose Theme");
            popupMenu.getMenu().add(0, 10, 15, "────────────");
            popupMenu.getMenu().add(0, 11, 16, "🔒 VPN Mode (Pro)");
            popupMenu.getMenu().add(0, 12, 17, "🚫 Advanced Ad Blocker (Pro)");
            popupMenu.getMenu().add(0, 13, 18, "────────────");
            popupMenu.getMenu().add(0, 15, 19, "⭐ Upgrade to LibreLynx Pro");
            
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        WebView webView = getActiveWebView();
                        if (webView != null && webView.canGoBack()) {
                            webView.goBack();
                        }
                        return true;
                    case 2:
                        webView = getActiveWebView();
                        if (webView != null && webView.canGoForward()) {
                            webView.goForward();
                        }
                        return true;
                    case 19:
                        refreshCurrentPage();
                        return true;
                    case 3:
                    case 7:
                    case 10:
                    case 13:
                    case 18:
                        return true;
                    case 4:
                        showBookmarksDialog();
                        return true;
                    case 5:
                        showDownloadsDialog();
                        return true;
                    case 6:
                        bookmarkCurrentPage();
                        return true;
                    case 8:
                        showSearchEngineDialog();
                        return true;
                    case 9:
                        showThemeDialog();
                        return true;
                    case 11:
                    case 12:
                        showProFeatureDialog(getProFeatureName(item.getItemId()));
                        return true;
                    case 15:
                        showProUpgradeDialog();
                        return true;
                    case 16:
                        openNewTab();
                        return true;
                    case 17:
                        showImprovedSwitchTabDialog(); // Updated to use improved dialog
                        return true;
                    case 20:
                        showHistoryDialog();
                        return true;
                    case 21: // Handle Clear All History
                        try {
                            browserHistory.clear();
                            SharedPreferences historyPrefs = getSharedPreferences("LibreLynxHistory", MODE_PRIVATE);
                            historyPrefs.edit().clear().apply();
                            Toast.makeText(this, "All history cleared", Toast.LENGTH_SHORT).show();
                            Log.d("MainActivity", "Cleared all history from popup menu");
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error clearing history: " + e.getMessage(), e);
                            Toast.makeText(this, "Error clearing history", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    default:
                        return false;
                }
            });
            
            WebView webView = getActiveWebView();
            popupMenu.getMenu().findItem(1).setEnabled(webView != null && webView.canGoBack());
            popupMenu.getMenu().findItem(2).setEnabled(webView != null && webView.canGoForward());
            popupMenu.getMenu().findItem(19).setEnabled(webView != null);
            popupMenu.getMenu().findItem(3).setEnabled(false);
            popupMenu.getMenu().findItem(7).setEnabled(false);
            popupMenu.getMenu().findItem(10).setEnabled(false);
            popupMenu.getMenu().findItem(13).setEnabled(false);
            popupMenu.getMenu().findItem(18).setEnabled(false);
            
            String currentUrl = webView != null ? webView.getUrl() : null;
            boolean hasValidUrl = currentUrl != null && !currentUrl.isEmpty() && 
                                 !currentUrl.equals("about:blank");
            popupMenu.getMenu().findItem(6).setEnabled(hasValidUrl);
            
            popupMenu.getMenu().findItem(11).setEnabled(false);
            popupMenu.getMenu().findItem(12).setEnabled(false);
            
            popupMenu.show();
        });
        
        urlEditText.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                navigateToUrl();
                return true;
            }
            return false;
        });
        Log.d("MainActivity", "Buttons set up");
    } catch (Exception e) {
        Log.e("MainActivity", "Error in setupButtons: " + e.getMessage(), e);
        Toast.makeText(this, "Error setting up buttons", Toast.LENGTH_LONG).show();
    }
    }

    // Refresh current page method
    private void refreshCurrentPage() {
        try {
            WebView webView = getActiveWebView();
            if (webView != null) {
                webView.reload();
                Toast.makeText(this, "Refreshing page...", Toast.LENGTH_SHORT).show();
                Log.d("MainActivity", "Refreshing current page");
            } else {
                Log.e("MainActivity", "No active WebView to refresh");
                Toast.makeText(this, "Error: No active tab", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error refreshing page: " + e.getMessage(), e);
            Toast.makeText(this, "Error refreshing page", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openNewTab() {
        try {
            WebView newWebView = new WebView(this);
            setupWebView(newWebView);
            tabs.add(newWebView);
            activeTabIndex = tabs.size() - 1;
            switchTab(activeTabIndex);
            loadSearchEngine();
            Toast.makeText(this, "New tab opened", Toast.LENGTH_SHORT).show();
            Log.d("MainActivity", "New tab opened, index: " + activeTabIndex);
        } catch (Exception e) {
            Log.e("MainActivity", "Error in openNewTab: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening new tab", Toast.LENGTH_SHORT).show();
        }
    }

    // Improved tab switching dialog
    private void showImprovedSwitchTabDialog() {
        try {
            List<TabItem> tabItems = new ArrayList<>();
            for (int i = 0; i < tabs.size(); i++) {
                WebView webView = tabs.get(i);
                String title = webView.getTitle() != null ? webView.getTitle() : "Tab " + (i + 1);
                String url = webView.getUrl() != null ? webView.getUrl() : "";
                tabItems.add(new TabItem(title, url, i));
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppTheme_Dialog);
            builder.setTitle("Tabs (" + tabs.size() + ")");
            
            GridView gridView = new GridView(this);
            gridView.setNumColumns(1);
            gridView.setAdapter(new TabAdapter(this, tabItems));
            
            builder.setView(gridView);
            builder.setPositiveButton("New Tab", (dialog, which) -> {
                openNewTab(); // Use existing method
                dialog.dismiss();
            });
            builder.setNegativeButton("Cancel", null);
            
            tabSwitchDialog = builder.create();
            tabSwitchDialog.show();
            
            Log.d("MainActivity", "Improved tabs dialog shown with " + tabs.size() + " tabs");
            
        } catch (Exception e) {
            Log.e("MainActivity", "Error in showImprovedSwitchTabDialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error showing tabs", Toast.LENGTH_SHORT).show();
        }
    }

    // Method to close individual tabs
    private void closeTab(int tabIndex) {
        try {
            if (tabs.size() <= 1) {
                Toast.makeText(this, "Cannot close last tab", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (tabIndex >= 0 && tabIndex < tabs.size()) {
                // Destroy the WebView to free memory
                WebView webViewToClose = tabs.get(tabIndex);
                if (webViewToClose != null) {
                    webViewToClose.clearHistory();
                    webViewToClose.clearCache(true);
                    webViewToClose.clearFormData();
                    webViewToClose.destroy();
                }
                
                tabs.remove(tabIndex);
                
                // Adjust active tab index if necessary
                if (tabIndex <= activeTabIndex) {
                    activeTabIndex = Math.max(0, activeTabIndex - 1);
                }
                
                // Switch to the adjusted active tab
                switchTab(activeTabIndex);
                
                Toast.makeText(this, "Tab closed", Toast.LENGTH_SHORT).show();
                Log.d("MainActivity", "Closed tab at index: " + tabIndex + ", remaining tabs: " + tabs.size());
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in closeTab: " + e.getMessage(), e);
            Toast.makeText(this, "Error closing tab", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showTabsDialog() {
        try {
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppTheme_Dialog);
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_tabs, null);
            builder.setView(dialogView);

            GridView gridView = dialogView.findViewById(R.id.tabs_grid);
            Button newTabButton = dialogView.findViewById(R.id.new_tab_button);

            if (gridView == null || newTabButton == null) {
                Log.e("TabsDialog", "Failed to find tabs_grid or new_tab_button in dialog_tabs.xml");
                Toast.makeText(this, "Error loading tabs dialog", Toast.LENGTH_SHORT).show();
                return;
            }

            // Dynamic column count based on screen width
            int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
            int columns = Math.max(2, screenWidthDp / 140); // At least 2 columns
            gridView.setNumColumns(columns);
            
            // Make sure GridView is properly configured for touch events
            gridView.setClickable(true);
            gridView.setFocusable(true);
            gridView.setFocusableInTouchMode(true);

            // Create and show dialog first
            AlertDialog dialog = builder.create();

            // Custom adapter for tabs with proper click handling
            ArrayAdapter<WebView> adapter = new ArrayAdapter<WebView>(this, R.layout.tab_item, tabs) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    View view = convertView;
                    if (view == null) {
                        view = LayoutInflater.from(getContext()).inflate(R.layout.tab_item, parent, false);
                    }

                    TextView titleView = view.findViewById(R.id.tab_title);
                    ImageButton closeButton = view.findViewById(R.id.tab_close_button);
                    ImageView faviconView = view.findViewById(R.id.tab_favicon);

                    if (titleView == null || closeButton == null || faviconView == null) {
                        Log.e("TabsDialog", "Failed to find tab views in tab_item.xml");
                        return view;
                    }

                    // Set tab title (use WebView title or URL)
                    WebView webView = tabs.get(position);
                    String title = webView.getTitle();
                    if (title == null || title.isEmpty() || title.equals("about:blank")) {
                        title = webView.getUrl() != null ? webView.getUrl() : "New Tab";
                    }
                    
                    // Truncate long titles
                    if (title.length() > 30) {
                        title = title.substring(0, 27) + "...";
                    }
                    titleView.setText(title);

                    // Apply theme-based text color
                    int textColorRes;
                    switch (currentTheme) {
                        case "ocean":
                            textColorRes = R.color.ocean_text_color;
                            break;
                        case "forest":
                            textColorRes = R.color.forest_text_color;
                            break;
                        case "sunset":
                            textColorRes = R.color.sunset_text_color;
                            break;
                        case "dark":
                        default:
                            textColorRes = R.color.dark_text_color;
                            break;
                    }
                    titleView.setTextColor(ContextCompat.getColor(MainActivity.this, textColorRes));

                    // Set favicon (placeholder or actual favicon)
                    faviconView.setImageResource(R.drawable.ic_webpage);
                    
                    // Make the main view clickable for tab switching
                    view.setClickable(true);
                    view.setFocusable(true);
                    view.setOnClickListener(v -> {
                        Log.d("TabsDialog", "Tab clicked at position: " + position);
                        try {
                            switchTab(position);
                            Toast.makeText(MainActivity.this, "Switched to tab: " + (position + 1), Toast.LENGTH_SHORT).show();
                            dialog.dismiss();
                        } catch (Exception e) {
                            Log.e("TabsDialog", "Error switching to tab: " + position, e);
                            Toast.makeText(MainActivity.this, "Error switching tab", Toast.LENGTH_SHORT).show();
                        }
                    });

                    // Close button click listener
                    closeButton.setOnClickListener(v -> {
                        Log.d("TabsDialog", "Close button clicked for tab at position: " + position);
                        if (tabs.size() > 1) {
                            tabs.remove(position);
                            if (activeTabIndex == position) {
                                activeTabIndex = Math.max(0, position - 1);
                                switchTab(activeTabIndex);
                            } else if (activeTabIndex > position) {
                                activeTabIndex--;
                            }
                            notifyDataSetChanged();
                            Toast.makeText(MainActivity.this, "Tab closed", Toast.LENGTH_SHORT).show();
                            Log.d("TabsDialog", "Tab closed, remaining tabs: " + tabs.size());
                        } else {
                            Toast.makeText(MainActivity.this, "Cannot close the last tab", Toast.LENGTH_SHORT).show();
                        }
                    });

                    // Highlight active tab
                    if (position == activeTabIndex) {
                        view.setBackgroundColor(ContextCompat.getColor(MainActivity.this, R.color.active_tab_background));
                    } else {
                        view.setBackgroundColor(ContextCompat.getColor(MainActivity.this, android.R.color.transparent));
                    }

                    return view;
                }
            };

            gridView.setAdapter(adapter);

            // New Tab button
            newTabButton.setOnClickListener(v -> {
                Log.d("TabsDialog", "New tab button clicked");
                openNewTab();
                adapter.notifyDataSetChanged();
            });

            dialog.show();
            Log.d("TabsDialog", "Tabs dialog shown successfully with " + tabs.size() + " tabs");
        } catch (Exception e) {
            Log.e("TabsDialog", "Error showing tabs dialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading tabs dialog", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void switchTab(int index) {
        try {
            activeTabIndex = index;
            ViewGroup webViewContainer = findViewById(R.id.webview);
            if (webViewContainer == null) {
                Log.e("MainActivity", "WebView container not found in switchTab");
                Toast.makeText(this, "Error: WebView container missing", Toast.LENGTH_LONG).show();
                return;
            }
            ((ViewGroup) webViewContainer).removeAllViews();
            WebView activeWebView = getActiveWebView();
            if (activeWebView != null) {
                ((ViewGroup) webViewContainer).addView(activeWebView);
                String currentUrl = activeWebView.getUrl();
                urlEditText.setText(currentUrl != null && !currentUrl.equals("about:blank") ? currentUrl : "");
                updateNavigationButtons();
                Log.d("MainActivity", "Switched to tab: " + index);
            } else {
                Log.e("MainActivity", "Active WebView is null in switchTab");
                Toast.makeText(this, "Error: No active tab", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in switchTab: " + e.getMessage(), e);
            Toast.makeText(this, "Error switching tab", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void bookmarkCurrentPage() {
        try {
            WebView webView = getActiveWebView();
            if (webView == null) {
                Log.e("Bookmark", "No active WebView");
                Toast.makeText(this, "No active tab to bookmark", Toast.LENGTH_SHORT).show();
                return;
            }
            String url = webView.getUrl();
            String title = webView.getTitle();
            
            if (url == null || url.isEmpty() || url.equals("about:blank")) {
                Log.w("Bookmark", "No valid page to bookmark");
                Toast.makeText(this, "No page to bookmark", Toast.LENGTH_SHORT).show();
                return;
            }
            
            SharedPreferences bookmarkPrefs = getSharedPreferences("LibreLynxBookmarks", MODE_PRIVATE);
            SharedPreferences.Editor editor = bookmarkPrefs.edit();
            editor.putString(url, title != null && !title.isEmpty() ? title : url);
            editor.apply();
            Log.d("Bookmark", "Bookmarked: URL=" + url + ", Title=" + title);
            Toast.makeText(this, "Page bookmarked!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("Bookmark", "Error saving bookmark: " + e.getMessage(), e);
            Toast.makeText(this, "Error saving bookmark", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showBookmarksDialog() {
        try {
            Log.d("BookmarksDialog", "Starting showBookmarksDialog");
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_engine, null);
            TextView titleView = dialogView.findViewById(R.id.dialog_title);
            GridView gridView = dialogView.findViewById(R.id.search_engine_list);
            
            if (titleView == null || gridView == null) {
                Log.e("BookmarksDialog", "Failed to find dialog_title or search_engine_list in dialog_search_engine.xml");
                Toast.makeText(this, "Error loading bookmarks dialog", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d("BookmarksDialog", "Dialog views inflated successfully");
            titleView.setText("Bookmarks");
            gridView.setNumColumns(1); // Single column for list-like display
            gridView.setClickable(true);
            gridView.setFocusable(true);
            gridView.setFocusableInTouchMode(true);
            
            SharedPreferences bookmarkPrefs = getSharedPreferences("LibreLynxBookmarks", MODE_PRIVATE);
            Map<String, ?> bookmarks = bookmarkPrefs.getAll();
            Log.d("BookmarksDialog", "Retrieved bookmarks, size: " + (bookmarks != null ? bookmarks.size() : 0));
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppTheme_Dialog);
            builder.setView(dialogView);
            builder.setNegativeButton("Close", (dialog, which) -> {
                Log.d("BookmarksDialog", "Bookmarks dialog closed");
            });
            
            // Create dialog before setting up adapter
            AlertDialog dialog = builder.create();
            
            if (bookmarks == null || bookmarks.isEmpty()) {
                Log.d("BookmarksDialog", "No bookmarks found, showing empty message");
                String[] emptyMessage = {"No bookmarks yet", "Bookmark pages from the menu!"};
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, emptyMessage);
                gridView.setAdapter(adapter);
                gridView.setOnItemClickListener(null); // Disable clicks for empty state
            } else {
                List<BookmarkItem> bookmarkList = new ArrayList<>();
                
                Log.d("BookmarksDialog", "Processing bookmarks");
                for (Map.Entry<String, ?> entry : bookmarks.entrySet()) {
                    String url = entry.getKey();
                    Object value = entry.getValue();
                    if (url != null && value instanceof String) {
                        String title = ((String) value).isEmpty() ? url : (String) value;
                        bookmarkList.add(new BookmarkItem(title, url));
                        Log.d("BookmarksDialog", "Added bookmark: URL=" + url + ", Title=" + title);
                    } else {
                        Log.w("BookmarksDialog", "Skipping invalid bookmark entry: key=" + url + ", value=" + value);
                    }
                }
                
                // Create custom adapter that handles dialog dismissal
                BookmarkAdapter adapter = new BookmarkAdapter(this, bookmarkList) {
                    
                    // Override showBookmarkMenu to refresh dialog after deletion
                    private void showBookmarkMenuWithRefresh(BookmarkItem bookmark, int position, View anchorView) {
                        try {
                            PopupMenu popupMenu = new PopupMenu(MainActivity.this, anchorView);
                            popupMenu.getMenu().add(0, 1, 0, "Open");
                            popupMenu.getMenu().add(0, 2, 1, "Delete");
                            
                            popupMenu.setOnMenuItemClickListener(item -> {
                                switch (item.getItemId()) {
                                    case 1:
                                        Log.d("BookmarkMenu", "Open selected for bookmark: " + bookmark.getUrl());
                                        if (isValidUrl(bookmark.getUrl())) {
                                            WebView webView = getActiveWebView();
                                            if (webView != null) {
                                                webView.loadUrl(bookmark.getUrl());
                                                urlEditText.setText(bookmark.getUrl());
                                                Toast.makeText(MainActivity.this, "Loading: " + bookmark.getTitle(), Toast.LENGTH_SHORT).show();
                                                dialog.dismiss();
                                            } else {
                                                Toast.makeText(MainActivity.this, "Error: No active tab", Toast.LENGTH_SHORT).show();
                                            }
                                        } else {
                                            Toast.makeText(MainActivity.this, "Invalid bookmark URL", Toast.LENGTH_SHORT).show();
                                        }
                                        return true;
                                    case 2:
                                        new AlertDialog.Builder(MainActivity.this, R.style.AppTheme_Dialog)
                                            .setTitle("Delete Bookmark")
                                            .setMessage("Delete this bookmark?\n\n" + bookmark.getTitle())
                                            .setPositiveButton("Delete", (d, w) -> {
                                                SharedPreferences bookmarkPrefs = getSharedPreferences("LibreLynxBookmarks", MODE_PRIVATE);
                                                bookmarkPrefs.edit().remove(bookmark.getUrl()).apply();
                                                Toast.makeText(MainActivity.this, "Bookmark deleted", Toast.LENGTH_SHORT).show();
                                                dialog.dismiss();
                                                showBookmarksDialog(); // Refresh the entire dialog
                                            })
                                            .setNegativeButton("Cancel", null)
                                            .show();
                                        return true;
                                    default:
                                        return false;
                                }
                            });
                            
                            popupMenu.show();
                        } catch (Exception e) {
                            Log.e("BookmarkMenu", "Error showing menu: " + e.getMessage(), e);
                            Toast.makeText(MainActivity.this, "Error showing menu", Toast.LENGTH_SHORT).show();
                        }
                    }
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        
                        // Override the CardView click to also dismiss dialog
                        androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) view;
                        TextView menuView = view.findViewById(R.id.bookmark_menu);
                        BookmarkItem bookmark = bookmarkList.get(position);
                        
                        cardView.setOnClickListener(v -> {
                            Log.d("BookmarkAdapter", "Bookmark clicked: " + bookmark.getUrl());
                            try {
                                if (isValidUrl(bookmark.getUrl())) {
                                    WebView webView = getActiveWebView();
                                    if (webView != null) {
                                        webView.loadUrl(bookmark.getUrl());
                                        urlEditText.setText(bookmark.getUrl());
                                        Log.d("BookmarkAdapter", "Loaded bookmark: " + bookmark.getUrl());
                                        Toast.makeText(MainActivity.this, "Loading: " + bookmark.getTitle(), Toast.LENGTH_SHORT).show();
                                        dialog.dismiss(); // Dismiss the dialog
                                    } else {
                                        Log.e("BookmarkAdapter", "No active WebView");
                                        Toast.makeText(MainActivity.this, "Error: No active tab", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Log.w("BookmarkAdapter", "Invalid URL: " + bookmark.getUrl());
                                    Toast.makeText(MainActivity.this, "Invalid bookmark URL", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e("BookmarkAdapter", "Error loading bookmark: " + e.getMessage(), e);
                                Toast.makeText(MainActivity.this, "Error loading bookmark", Toast.LENGTH_SHORT).show();
                            }
                        });
                        
                        // Override menu click to use the custom method with dialog refresh
                        if (menuView != null) {
                            menuView.setOnClickListener(v -> {
                                Log.d("BookmarkAdapter", "Menu clicked for bookmark: " + bookmark.getUrl());
                                showBookmarkMenuWithRefresh(bookmark, position, v);
                            });
                        }
                        
                        return view;
                    }
                };
                
                gridView.setAdapter(adapter);
                
                builder.setNeutralButton("Clear All", (dialogInterface, which) -> {
                    try {
                        bookmarkPrefs.edit().clear().apply();
                        Toast.makeText(this, "All bookmarks cleared", Toast.LENGTH_SHORT).show();
                        Log.d("BookmarksDialog", "Cleared all bookmarks");
                        dialog.dismiss();
                        showBookmarksDialog(); // Refresh dialog
                    } catch (Exception e) {
                        Log.e("BookmarksDialog", "Error clearing bookmarks: " + e.getMessage(), e);
                        Toast.makeText(this, "Error clearing bookmarks", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            dialog.show();
            Log.d("BookmarksDialog", "Bookmarks dialog shown successfully");
        } catch (Exception e) {
            Log.e("BookmarksDialog", "Error in showBookmarksDialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading bookmarks dialog", Toast.LENGTH_SHORT).show();
        }
    }

    // Show history dialog
    private void showHistoryDialog() {
        try {
            Log.d("HistoryDialog", "Starting showHistoryDialog");
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_engine, null);
            TextView titleView = dialogView.findViewById(R.id.dialog_title);
            GridView gridView = dialogView.findViewById(R.id.search_engine_list);
            
            if (titleView == null || gridView == null) {
                Log.e("HistoryDialog", "Failed to find dialog_title or search_engine_list in dialog_search_engine.xml");
                Toast.makeText(this, "Error loading history dialog", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d("HistoryDialog", "Dialog views inflated successfully");
            titleView.setText("History");
            gridView.setNumColumns(1); // Single column for list-like display
            gridView.setClickable(true);
            gridView.setFocusable(true);
            gridView.setFocusableInTouchMode(true);
            
            SharedPreferences historyPrefs = getSharedPreferences("LibreLynxHistory", MODE_PRIVATE);
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppTheme_Dialog);
            builder.setView(dialogView);
            builder.setNegativeButton("Close", (dialog, which) -> {
                Log.d("HistoryDialog", "History dialog closed");
            });
            
            // Create dialog before setting up adapter
            AlertDialog dialog = builder.create();
            
            if (browserHistory.isEmpty()) {
                Log.d("HistoryDialog", "No history found, showing empty message");
                String[] emptyMessage = {"No history yet", "Visit some websites to see them here!"};
                ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, emptyMessage);
                gridView.setAdapter(adapter);
                gridView.setOnItemClickListener(null); // Disable clicks for empty state
            } else {
                List<BookmarkItem> historyList = new ArrayList<>();
                
                Log.d("HistoryDialog", "Processing history items");
                for (String url : browserHistory) {
                    String title = historyPrefs.getString(url, url);
                    historyList.add(new BookmarkItem(title, url));
                    Log.d("HistoryDialog", "Added history item: URL=" + url + ", Title=" + title);
                }
                
                // Create custom adapter that handles dialog dismissal
                BookmarkAdapter adapter = new BookmarkAdapter(this, historyList) {
                    @Override
                    public View getView(int position, View convertView, ViewGroup parent) {
                        View view = super.getView(position, convertView, parent);
                        
                        // Override the CardView click to also dismiss dialog
                        androidx.cardview.widget.CardView cardView = (androidx.cardview.widget.CardView) view;
                        TextView menuView = view.findViewById(R.id.bookmark_menu);
                        BookmarkItem historyItem = historyList.get(position);
                        
                        cardView.setOnClickListener(v -> {
                            Log.d("HistoryAdapter", "History item clicked: " + historyItem.getUrl());
                            try {
                                if (isValidUrl(historyItem.getUrl())) {
                                    WebView webView = getActiveWebView();
                                    if (webView != null) {
                                        webView.loadUrl(historyItem.getUrl());
                                        urlEditText.setText(historyItem.getUrl());
                                        Log.d("HistoryAdapter", "Loaded history item: " + historyItem.getUrl());
                                        Toast.makeText(MainActivity.this, "Loading: " + historyItem.getTitle(), Toast.LENGTH_SHORT).show();
                                        dialog.dismiss(); // Dismiss the dialog
                                    } else {
                                        Log.e("HistoryAdapter", "No active WebView");
                                        Toast.makeText(MainActivity.this, "Error: No active tab", Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Log.w("HistoryAdapter", "Invalid URL: " + historyItem.getUrl());
                                    Toast.makeText(MainActivity.this, "Invalid history URL", Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                Log.e("HistoryAdapter", "Error loading history item: " + e.getMessage(), e);
                                Toast.makeText(MainActivity.this, "Error loading history item", Toast.LENGTH_SHORT).show();
                            }
                        });
                        
                        // Override menu click for history-specific actions
                        if (menuView != null) {
                            menuView.setOnClickListener(v -> {
                                Log.d("HistoryAdapter", "Menu clicked for history item: " + historyItem.getUrl());
                                showHistoryMenu(historyItem, position, v, dialog);
                            });
                        }
                        
                        return view;
                    }
                };
                
                gridView.setAdapter(adapter);
                
                builder.setNeutralButton("Clear All", (dialogInterface, which) -> {
                    try {
                        browserHistory.clear();
                        historyPrefs.edit().clear().apply();
                        Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
                        Log.d("HistoryDialog", "Cleared all history");
                        dialog.dismiss();
                    } catch (Exception e) {
                        Log.e("HistoryDialog", "Error clearing history: " + e.getMessage(), e);
                        Toast.makeText(this, "Error clearing history", Toast.LENGTH_SHORT).show();
                    }
                });
            }
            
            dialog.show();
            Log.d("HistoryDialog", "History dialog shown successfully");
        } catch (Exception e) {
            Log.e("HistoryDialog", "Error in showHistoryDialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading history dialog", Toast.LENGTH_SHORT).show();
        }
    }

    // Show history menu
    private void showHistoryMenu(BookmarkItem historyItem, int position, View anchorView, AlertDialog parentDialog) {
        try {
            PopupMenu popupMenu = new PopupMenu(this, anchorView);
            popupMenu.getMenu().add(0, 1, 0, "Open");
            popupMenu.getMenu().add(0, 2, 1, "Remove from History");
            
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        Log.d("HistoryMenu", "Open selected for history item: " + historyItem.getUrl());
                        if (isValidUrl(historyItem.getUrl())) {
                            WebView webView = getActiveWebView();
                            if (webView != null) {
                                webView.loadUrl(historyItem.getUrl());
                                urlEditText.setText(historyItem.getUrl());
                                Toast.makeText(this, "Loading: " + historyItem.getTitle(), Toast.LENGTH_SHORT).show();
                                parentDialog.dismiss();
                            } else {
                                Toast.makeText(this, "Error: No active tab", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "Invalid history URL", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    case 2:
                        // Show confirmation dialog for deletion
                        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                            .setTitle("Remove from History")
                            .setMessage("Remove this item from history?\n\n" + historyItem.getTitle())
                            .setPositiveButton("Remove", (d, w) -> {
                                browserHistory.remove(historyItem.getUrl());
                                SharedPreferences historyPrefs = getSharedPreferences("LibreLynxHistory", MODE_PRIVATE);
                                SharedPreferences.Editor editor = historyPrefs.edit();
                                editor.remove(historyItem.getUrl());
                                
                                // Update the ordered list
                                StringBuilder historyString = new StringBuilder();
                                for (int i = 0; i < browserHistory.size(); i++) {
                                    if (i > 0) historyString.append("|");
                                    historyString.append(browserHistory.get(i));
                                }
                                editor.putString("history_order", historyString.toString());
                                editor.apply();
                                
                                Toast.makeText(this, "Removed from history", Toast.LENGTH_SHORT).show();
                                parentDialog.dismiss();
                                showHistoryDialog(); // Refresh the dialog
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                        return true;
                    default:
                        return false;
                }
            });
            
            popupMenu.show();
        } catch (Exception e) {
            Log.e("HistoryMenu", "Error showing history menu: " + e.getMessage(), e);
            Toast.makeText(this, "Error showing history menu", Toast.LENGTH_SHORT).show();
        }
    }

    private void showBookmarkMenu(BookmarkItem bookmark, int position, View anchorView) {
        try {
            PopupMenu popupMenu = new PopupMenu(this, anchorView); // Use the passed anchor view
            popupMenu.getMenu().add(0, 1, 0, "Open");
            popupMenu.getMenu().add(0, 2, 1, "Delete");
            
            popupMenu.setOnMenuItemClickListener(item -> {
                switch (item.getItemId()) {
                    case 1:
                        Log.d("BookmarkMenu", "Open selected for bookmark: " + bookmark.getUrl());
                        if (isValidUrl(bookmark.getUrl())) {
                            WebView webView = getActiveWebView();
                            if (webView != null) {
                                webView.loadUrl(bookmark.getUrl());
                                urlEditText.setText(bookmark.getUrl());
                                Log.d("BookmarkMenu", "Opened bookmark: " + bookmark.getUrl());
                                Toast.makeText(this, "Loading: " + bookmark.getTitle(), Toast.LENGTH_SHORT).show();
                            } else {
                                Log.e("BookmarkMenu", "No active WebView");
                                Toast.makeText(this, "Error: No active tab", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Log.w("BookmarkMenu", "Invalid URL: " + bookmark.getUrl());
                            Toast.makeText(this, "Invalid bookmark URL", Toast.LENGTH_SHORT).show();
                        }
                        return true;
                    case 2:
                        // Show confirmation dialog for deletion
                        new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                            .setTitle("Delete Bookmark")
                            .setMessage("Are you sure you want to delete this bookmark?\n\n" + bookmark.getTitle())
                            .setPositiveButton("Delete", (dialog, which) -> {
                                SharedPreferences bookmarkPrefs = getSharedPreferences("LibreLynxBookmarks", MODE_PRIVATE);
                                bookmarkPrefs.edit().remove(bookmark.getUrl()).apply();
                                Toast.makeText(this, "Bookmark deleted", Toast.LENGTH_SHORT).show();
                                Log.d("BookmarkMenu", "Deleted bookmark: " + bookmark.getUrl());
                                // Note: You might want to refresh the dialog here or pass a callback to update the list
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                        return true;
                    default:
                        return false;
                }
            });
            
            popupMenu.show();
            Log.d("BookmarkMenu", "Bookmark menu shown for: " + bookmark.getUrl());
        } catch (Exception e) {
            Log.e("BookmarkMenu", "Error in showBookmarkMenu: " + e.getMessage(), e);
            Toast.makeText(this, "Error showing bookmark menu: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private boolean isValidUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://");
    }
    
    private void showDownloadsDialog() {
        Toast.makeText(this, "Downloads feature - coming soon!\nFor now, use your browser's default download handling.", Toast.LENGTH_LONG).show();
    }
    
    private String getProFeatureName(int itemId) {
        switch (itemId) {
            case 11: return "VPN Mode";
            case 12: return "Advanced Ad Blocker";
            default: return "Pro Feature";
        }
    }
    
    private void showProFeatureDialog(String featureName) {
        try {
            new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("Upgrade to Pro")
                .setMessage(featureName + " is available in LibreLynx Pro.\n\nPro features include:\n• Built-in VPN\n• Advanced Ad Blocking")
                .setPositiveButton("Get Pro", (dialog, which) -> showProUpgradeDialog())
                .setNegativeButton("Maybe Later", null)
                .show();
            Log.d("ProFeatureDialog", "Pro feature dialog shown for: " + featureName);
        } catch (Exception e) {
            Log.e("ProFeatureDialog", "Error showing pro feature dialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading pro feature dialog", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showProUpgradeDialog() {
        try {
            new AlertDialog.Builder(this, R.style.AppTheme_Dialog)
                .setTitle("LibreLynx Pro")
                .setMessage("Upgrade to LibreLynx Pro for advanced privacy features:\n\n• Built-in VPN Protection\n• Advanced Ad & Tracker Blocking\n• No Ads")
                .setPositiveButton("Visit Store", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("https://librelynx.website"));
                        startActivity(intent);
                        Log.d("ProUpgradeDialog", "Opened store URL");
                    } catch (Exception e) {
                        Log.e("ProUpgradeDialog", "Error opening store URL: " + e.getMessage(), e);
                        Toast.makeText(this, "Visit librelynx.com/pro to upgrade", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Not Now", null)
                .show();
            Log.d("ProUpgradeDialog", "Pro upgrade dialog shown");
        } catch (Exception e) {
            Log.e("ProUpgradeDialog", "Error showing pro upgrade dialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading pro upgrade dialog", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void navigateToUrl() {
        try {
            String input = urlEditText.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, R.string.enter_url, Toast.LENGTH_SHORT).show();
                return;
            }
            
            String url;
            if (isUrl(input)) {
                if (!input.startsWith("http://") && !input.startsWith("https://")) {
                    url = "https://" + input;
                } else {
                    url = input;
                }
            } else {
                url = getSearchUrl(input);
            }
            
            WebView webView = getActiveWebView();
            if (webView != null) {
                webView.loadUrl(url);
                Log.d("Navigate", "Loading URL: " + url);
            } else {
                Log.e("Navigate", "No active WebView");
                Toast.makeText(this, "Error: No active tab", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Log.e("Navigate", "Error in navigateToUrl: " + e.getMessage(), e);
            Toast.makeText(this, "Error navigating to URL", Toast.LENGTH_SHORT).show();
        }
    }
    
    private boolean isUrl(String input) {
        return input.contains(".") && !input.contains(" ") && 
               (input.startsWith("http") || input.contains("www") || input.matches(".*\\.[a-zA-Z]{2,}.*"));
    }
    
    private String getSearchUrl(String query) {
    String encodedQuery = Uri.encode(query);
    switch (currentSearchEngine) {
        case "brave":
            return BRAVE_SEARCH + encodedQuery;
        case "mojeek":
            return MOJEEK_SEARCH + encodedQuery;
        default:
            return BRAVE_SEARCH + encodedQuery; // default search engine
    }
}
    private void loadSearchEngine() {
        try {
            WebView webView = getActiveWebView();
            if (webView == null) {
                Log.e("MainActivity", "No active WebView to load search engine");
                Toast.makeText(this, "Error: No active tab", Toast.LENGTH_SHORT).show();
                return;
            }
            String url;
switch (currentSearchEngine) {
    case "brave":
        url = "https://search.brave.com";
        break;
    case "mojeek":
        url = "https://www.mojeek.com";
        break;
    default:
        url = "https://search.brave.com"; // default fallback
        break;
}
webView.loadUrl(url);
urlEditText.setText("");
Log.d("MainActivity", "Loaded search engine: " + currentSearchEngine + ", URL: " + url);

        } catch (Exception e) {
            Log.e("MainActivity", "Error in loadSearchEngine: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading search engine", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showSearchEngineDialog() {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_engine, null);
            TextView titleView = dialogView.findViewById(R.id.dialog_title);
            GridView gridView = dialogView.findViewById(R.id.search_engine_list);
            
            if (titleView == null || gridView == null) {
                Log.e("SearchEngineDialog", "Failed to find dialog_title or search_engine_list in dialog_search_engine.xml");
                Toast.makeText(this, "Error loading search engine dialog", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d("SearchEngineDialog", "Dialog views inflated successfully");
titleView.setText(prefs.getBoolean(KEY_FIRST_LAUNCH, true) ? "Choose Default Search Engine" : getString(R.string.search_engine_title));
gridView.setNumColumns(1); // Single column for list-like display

String[] searchEngines = {"Brave Search", "Mojeek"};
            
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, searchEngines) {
                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getView(position, convertView, parent);
                    int backgroundRes = R.drawable.search_engine_item_background_dark; // Fallback
                    try {
                        switch (currentTheme) {
                            case "ocean":
                                backgroundRes = R.drawable.search_engine_item_background_ocean;
                                break;
                            case "forest":
                                backgroundRes = R.drawable.search_engine_item_background_forest;
                                break;
                            case "sunset":
                                backgroundRes = R.drawable.search_engine_item_background_sunset;
                                break;
                            case "dark":
                                backgroundRes = R.drawable.search_engine_item_background_dark;
                                break;
                        }
                        view.setBackgroundResource(backgroundRes);
                        view.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.dialog_text_color));
                        view.setPadding(8, 8, 8, 8);
                        Log.d("SearchEngineDialog", "Applied background to item: " + getItem(position) + " for theme: " + currentTheme);
                    } catch (Exception e) {
                        Log.e("SearchEngineDialog", "Error setting grid item background for theme: " + currentTheme + ", using fallback", e);
                    }
                    return view;
                }
            };
            
            gridView.setAdapter(adapter);
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppTheme_Dialog);
            builder.setView(dialogView);
            
            AlertDialog dialog = builder.create();
            
            gridView.setOnItemClickListener((parent, view, which, id) -> {
                try {
                    switch (which) {
                        
                        case 0:
                            currentSearchEngine = "brave";
                            break;
                        case 1:
                            currentSearchEngine = "mojeek";
                            break;
                    }
                    
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("search_engine", currentSearchEngine);
                    if (prefs.getBoolean(KEY_FIRST_LAUNCH, true)) {
                        editor.putBoolean(KEY_FIRST_LAUNCH, false);
                    }
                    editor.apply();
                    
                    Toast.makeText(MainActivity.this, R.string.search_engine_updated, Toast.LENGTH_SHORT).show();
                    Log.d("SearchEngineDialog", "Search engine updated to: " + currentSearchEngine);
                    
                    loadSearchEngine();
                    dialog.dismiss();
                } catch (Exception e) {
                    Log.e("SearchEngineDialog", "Error selecting search engine at position " + which + ": " + e.getMessage(), e);
                    Toast.makeText(this, "Error updating search engine", Toast.LENGTH_SHORT).show();
                }
            });
            
            if (prefs.getBoolean(KEY_FIRST_LAUNCH, true)) {
                dialog.setCancelable(false);
            }
            
            dialog.show();
            Log.d("SearchEngineDialog", "Search engine dialog shown");
        } catch (Exception e) {
            Log.e("SearchEngineDialog", "Error showing search engine dialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading search engine dialog", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showThemeDialog() {
        try {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_search_engine, null);
            TextView titleView = dialogView.findViewById(R.id.dialog_title);
            GridView gridView = dialogView.findViewById(R.id.search_engine_list);
            
            if (titleView == null || gridView == null) {
                Log.e("ThemeDialog", "Failed to find dialog_title or search_engine_list in dialog_search_engine.xml");
                Toast.makeText(this, "Error loading theme dialog", Toast.LENGTH_SHORT).show();
                return;
            }
            
            Log.d("ThemeDialog", "Dialog views inflated successfully");
            titleView.setText("Choose Theme");
            gridView.setNumColumns(1); // Single column for list-like display
            
            String[] themes = {"Dark", "Ocean", "Forest", "Sunset"};

ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, themes) {
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        TextView view = (TextView) super.getView(position, convertView, parent);
        int backgroundRes;
        switch (themes[position]) {
            case "Ocean":
                backgroundRes = R.drawable.search_engine_item_background_ocean;
                break;
            case "Forest":
                backgroundRes = R.drawable.search_engine_item_background_forest;
                break;
            case "Sunset":
                backgroundRes = R.drawable.search_engine_item_background_sunset;
                break;
            case "Dark":
            default:
                backgroundRes = R.drawable.search_engine_item_background_dark;
                break;
        }
        try {
            view.setBackgroundResource(backgroundRes);
            view.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.dialog_text_color));
            view.setPadding(8, 8, 8, 8);
        } catch (Exception e) {
            Log.e("ThemeDialog", "Error setting grid item background for theme: " + themes[position], e);
        }
        return view;
    }
};
gridView.setAdapter(adapter);
            
            AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AppTheme_Dialog);
            builder.setView(dialogView);
            
            AlertDialog dialog = builder.create();
            
            gridView.setOnItemClickListener((parent, view, which, id) -> {
                try {
                    switch (which) {
                        case 0:
                            currentTheme = "dark";
                            setTheme(R.style.AppTheme_Dark);
                            break;
                        case 1:
                            currentTheme = "ocean";
                            setTheme(R.style.AppTheme_Ocean);
                            break;
                        case 2:
                            currentTheme = "forest";
                            setTheme(R.style.AppTheme_Forest);
                            break;
                        case 3:
                            currentTheme = "sunset";
                            setTheme(R.style.AppTheme_Sunset);
                            break;
                    }
                    
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString(KEY_THEME, currentTheme);
                    editor.apply();
                    
                    Toast.makeText(MainActivity.this, "Theme updated, please restart the app", Toast.LENGTH_SHORT).show();
                    Log.d("ThemeDialog", "Theme updated to: " + currentTheme);
                    
                    dialog.dismiss();
                    recreate();
                } catch (Exception e) {
                    Log.e("ThemeDialog", "Error selecting theme at position " + which + ": " + e.getMessage(), e);
                    Toast.makeText(this, "Error updating theme", Toast.LENGTH_SHORT).show();
                }
            });
            
            dialog.show();
            Log.d("ThemeDialog", "Theme dialog shown");
        } catch (Exception e) {
            Log.e("ThemeDialog", "Error showing theme dialog: " + e.getMessage(), e);
            Toast.makeText(this, "Error loading theme dialog", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateNavigationButtons() {
        try {
            WebView webView = getActiveWebView();
            if (webView != null) {
                Log.d("MainActivity", "Updating navigation buttons: canGoBack=" + webView.canGoBack() + 
                      ", canGoForward=" + webView.canGoForward());
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in updateNavigationButtons: " + e.getMessage(), e);
        }
    }
    
    private void checkPermissions() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) 
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.INTERNET}, PERMISSION_REQUEST_CODE);
                Log.d("MainActivity", "Requesting INTERNET permission");
            } else {
                Log.d("MainActivity", "INTERNET permission already granted");
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in checkPermissions: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void onBackPressed() {
        try {
            WebView webView = getActiveWebView();
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else if (tabs.size() > 1) {
                tabs.remove(activeTabIndex);
                activeTabIndex = Math.max(0, activeTabIndex - 1);
                switchTab(activeTabIndex);
            } else {
                super.onBackPressed();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error in onBackPressed: " + e.getMessage(), e);
            super.onBackPressed();
        }
    }
    
    @Override
    protected void onDestroy() {
        try {
            for (WebView webView : tabs) {
                if (webView != null) {
                    webView.clearHistory();
                    webView.clearCache(true);
                    webView.clearFormData();
                    webView.destroy();
                }
            }
            tabs.clear();
            Log.d("MainActivity", "Destroyed WebViews and cleared tabs");
        } catch (Exception e) {
            Log.e("MainActivity", "Error in onDestroy: " + e.getMessage(), e);
        }
        super.onDestroy();
    }
    
    private class PrivacyWebViewClient extends WebViewClient {
        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            try {
                super.onPageStarted(view, url, favicon);
                urlEditText.setText(url);
                progressBar.setVisibility(View.VISIBLE);
                updateNavigationButtons();
                Log.d("PrivacyWebViewClient", "Page started: " + url);
            } catch (Exception e) {
                Log.e("PrivacyWebViewClient", "Error in onPageStarted: " + e.getMessage(), e);
            }
        }
        
        @Override
        public void onPageFinished(WebView view, String url) {
            try {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
                updateNavigationButtons();
                
                // Inject CSS for search engines after page loads
                if (isSearchEngineUrl(url)) {
                    view.postDelayed(() -> injectMinimalCSS(view), 100);
                }
                
                // Add to history when page finishes loading
                String title = view.getTitle();
                addToHistory(url, title);
                
                Log.d("PrivacyWebViewClient", "Page finished: " + url);
            } catch (Exception e) {
                Log.e("PrivacyWebViewClient", "Error in onPageFinished: " + e.getMessage(), e);
            }
        }
        

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        try {
            if (isTrackingDomain(url)) {
                Log.w("PrivacyWebViewClient", "Blocked tracker: " + url);
                Toast.makeText(MainActivity.this, R.string.blocked_tracker, Toast.LENGTH_SHORT).show();
                return true;
            }

            Intent intent = null;

            // YouTube
            if (url.contains("youtube.com") || url.contains("youtu.be")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.google.android.youtube");
            }

            // Twitter
            else if (url.contains("twitter.com")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.twitter.android");
            }

            // Instagram
            else if (url.contains("instagram.com")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.instagram.android");
            }

            // Facebook
            else if (url.contains("facebook.com")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.facebook.katana");
            }

            // Reddit
            else if (url.contains("reddit.com")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.reddit.frontpage");
            }

            // Spotify
            else if (url.contains("spotify.com")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.spotify.music");
            }

            // Netflix
            else if (url.contains("netflix.com")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.netflix.mediaclient");
            }

            // Amazon
            else if (url.contains("amazon.com") || url.contains("amazon.co.uk")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.amazon.mShop.android.shopping");
            }

            // GitHub
            else if (url.contains("github.com")) {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.setPackage("com.github.android");
            }

            // Fallback: open in system browser if matched but app missing
            if (intent != null) {
                try {
                    MainActivity.this.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    MainActivity.this.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                }
                return true;
            }

            return false;
        } catch (Exception e) {
            Log.e("PrivacyWebViewClient", "Error in shouldOverrideUrlLoading: " + e.getMessage(), e);
            return false;
        }
    }
           private boolean isTrackingDomain(String url) {
            String[] trackers = {
                "google-analytics.com",
                "googletagmanager.com", 
                "facebook.com/tr",
                "doubleclick.net",
                "googlesyndication.com"
            };
            
            for (String tracker : trackers) {
                if (url.contains(tracker)) {
                    return true;
                }
            }
            return false;
        }
    }
}
