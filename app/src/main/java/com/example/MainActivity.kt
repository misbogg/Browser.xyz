package com.example

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.GeminiApiManager
import com.example.browser.LoadedResource
import com.example.browser.ResourceType
import com.example.data.Bookmark
import com.example.data.CustomScript
import com.example.data.Tab
import com.example.ui.BrowserViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BrowserAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserAppScreen(viewModel: BrowserViewModel = viewModel()) {
    val currentUrl by viewModel.currentUrl.collectAsStateWithLifecycle()
    val currentTitle by viewModel.currentTitle.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()

    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val currentTabId by viewModel.currentTabId.collectAsStateWithLifecycle()

    // Control toggles
    val isAbEnabled by viewModel.isAdBlockEnabled.collectAsStateWithLifecycle()
    val isCbEnabled by viewModel.isCookieBlockEnabled.collectAsStateWithLifecycle()
    val isJsEnabled by viewModel.isJsEnabled.collectAsStateWithLifecycle()
    val isFpEnabled by viewModel.isFingerprintShieldEnabled.collectAsStateWithLifecycle()
    val isHttpsEnf by viewModel.isHttpsEnforced.collectAsStateWithLifecycle()
    val isDesktop by viewModel.isDesktopMode.collectAsStateWithLifecycle()

    // Page stats
    val adBlocks by viewModel.adBlockCount.collectAsStateWithLifecycle()
    val trackerBlocks by viewModel.trackerBlockCount.collectAsStateWithLifecycle()
    val scriptBlocks by viewModel.scriptBlockCount.collectAsStateWithLifecycle()
    val cookieBlocks by viewModel.cookieBlockCount.collectAsStateWithLifecycle()
    val fpBlocks by viewModel.fingerprintBlockCount.collectAsStateWithLifecycle()
    val redirectsCount by viewModel.redirectCount.collectAsStateWithLifecycle()
    val privacyScore by viewModel.privacyScore.collectAsStateWithLifecycle()
    val isSecure by viewModel.isHttpsSecure.collectAsStateWithLifecycle()

    // VPN & Proxy state variables for address bar active badge
    val vpnEnabledState by viewModel.vpnEnabled.collectAsStateWithLifecycle()
    val customProxyEnabledState by viewModel.customProxyEnabled.collectAsStateWithLifecycle()

    // Dialogs / Sheets controllers
    var showTabsOverlay by remember { mutableStateOf(false) }
    var showDashboardSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showScriptsDialog by remember { mutableStateOf(false) }
    var showAiAssistantDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showAiShoppingSheet by remember { mutableStateOf(false) }
    var shoppingAssistantInput by remember { mutableStateOf("") }
    var showBiometricSimulator by remember { mutableStateOf(false) }

    // Navigation and address bar typing state
    var addressInput by remember { mutableStateOf(currentUrl) }
    var isEditingAddress by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Sync input field when url resolves
    LaunchedEffect(currentUrl) {
        if (!isEditingAddress) {
            addressInput = currentUrl
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main scaffold holding upper address bar and standard WebView
        Scaffold(
            topBar = {
                Column {
                    // Small spacer for status bar
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    
                    // Double Action Top Address Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Floating security indicator & Dashboard launcher
                        Box(
                            modifier = Modifier
                                .padding(end = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        !isSecure -> DangerRed.copy(alpha = 0.2f)
                                        redirectsCount > 4 -> WarningYellow.copy(alpha = 0.2f)
                                        else -> SecureGreen.copy(alpha = 0.2f)
                                    }
                                )
                                .clickable { showDashboardSheet = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                !isSecure -> DangerRed
                                                redirectsCount > 4 -> WarningYellow
                                                else -> SecureGreen
                                            }
                                        )
                                )
                                Text(
                                    text = "$privacyScore",
                                    color = when {
                                        !isSecure -> DangerRed
                                        redirectsCount > 4 -> WarningYellow
                                        else -> SecureGreen
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Encrypted VPN / Proxy Shield Active Badge Shortcut
                        if (vpnEnabledState || customProxyEnabledState) {
                            val activeServerCountryCode by viewModel.vpnServerCountryCode.collectAsStateWithLifecycle()
                            Box(
                                modifier = Modifier
                                    .padding(end = 6.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CyanNeon.copy(alpha = 0.2f))
                                    .clickable { showSettingsSheet = true }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.VpnLock, 
                                        contentDescription = "VPN Tunnel Connected", 
                                        tint = CyanNeon,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = if (vpnEnabledState) activeServerCountryCode else "PRX",
                                        color = CyanNeon,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // URL Search input
                        TextField(
                            value = addressInput,
                            onValueChange = { addressInput = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .testTag("address_bar_input"),
                            placeholder = { Text("Search or type URL", fontSize = 14.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent
                            ),
                            shape = RoundedCornerShape(25.dp),
                            trailingIcon = {
                                if (isLoading) {
                                    IconButton(onClick = { webViewRef?.stopLoading() }) {
                                        Icon(Icons.Default.Close, contentDescription = "Stop", tint = MaterialTheme.colorScheme.primary)
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            isEditingAddress = false
                                            viewModel.onUrlEntered(addressInput)
                                        }
                                    ) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = "Go", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        )

                        // Quick access tab grid launcher badge
                        IconButton(
                            onClick = { showTabsOverlay = true },
                            modifier = Modifier.padding(start = 4.dp).testTag("tabs_badge_button")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(28.dp)
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = "${tabs.size}",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Progress slider line
                    if (isLoading) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().height(2.dp).testTag("progress_indicator"),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    } else {
                        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            },
            bottomBar = {
                // Bottom control browser bar
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.ArrowBack, contentDescription = "Back") },
                        selected = false,
                        onClick = { if (webViewRef?.canGoBack() == true) webViewRef?.goBack() }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.ArrowForward, contentDescription = "Forward") },
                        selected = false,
                        onClick = { if (webViewRef?.canGoForward() == true) webViewRef?.goForward() }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        selected = false,
                        onClick = { viewModel.onUrlEntered("https://google.com") }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.BookmarkBorder, contentDescription = "Bookmark") },
                        selected = false,
                        onClick = { viewModel.toggleBookmarkCurrent() }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Shield Dashboard") },
                        label = { Text("Shield", fontSize = 9.sp) },
                        selected = showDashboardSheet,
                        onClick = { showDashboardSheet = true }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Menu, contentDescription = "Settings menu") },
                        selected = false,
                        onClick = { showSettingsSheet = true }
                    )
                }
            }
        ) { innerPadding ->
            // Web view viewport
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            
                            // Setup default WebSettings
                            settings.apply {
                                javaScriptEnabled = isJsEnabled
                                domStorageEnabled = true
                                databaseEnabled = true
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    url?.let { viewModel.onPageStarted(it) }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    url?.let {
                                        viewModel.onPageFinished(it, view?.title ?: "")
                                        
                                        // Inject Custom Settings CSS rules
                                        val domain = Uri.parse(it).host ?: ""
                                        coroutineScope.launch {
                                            val rule = viewModel.siteRules.value.find { r -> r.domain == domain }
                                            if (rule != null && rule.customCss.isNotEmpty()) {
                                                val cssScript = """
                                                    (function() {
                                                        var style = document.createElement('style');
                                                        style.innerHTML = `${rule.customCss.replace("`", "\\`").replace("$", "\\$")}`;
                                                        document.head.appendChild(style);
                                                    })();
                                                """.trimIndent()
                                                view?.evaluateJavascript(cssScript, null)
                                            }
                                        }
                                    }
                                }

                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                    val url = request?.url?.toString()
                                    if (url != null) {
                                        // Check user setting HTTPS enforcement
                                        if (isHttpsEnf && url.startsWith("http://")) {
                                            val secureUrl = url.replace("http://", "https://")
                                            view?.loadUrl(secureUrl)
                                            return true
                                        }
                                    }
                                    return false
                                }

                                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                    if (request != null) {
                                        val blockRes = viewModel.browserEngine.interceptRequest(request)
                                        if (blockRes != null) return blockRes
                                    }
                                    return super.shouldInterceptRequest(view, request)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    viewModel.onProgressChanged(newProgress)
                                }
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    // Handle frame title updates
                                }
                            }

                            webViewRef = this
                            loadUrl(currentUrl)
                        }
                    },
                    update = { view ->
                        // Dynamically update user agent from Viewmodel state
                        val resolvedUAStr = viewModel.getUserAgentString()
                        if (view.settings.userAgentString != resolvedUAStr) {
                            view.settings.userAgentString = resolvedUAStr
                            view.reload()
                        }

                        // Dynamically update Javascript enable
                        if (view.settings.javaScriptEnabled != isJsEnabled) {
                            view.settings.javaScriptEnabled = isJsEnabled
                            view.reload()
                        }

                        // Perform programmatic load when VM URL deviates from webview current state
                        if (view.url != currentUrl) {
                            view.loadUrl(currentUrl)
                        }
                    },
                    modifier = Modifier.fillMaxSize().testTag("browser_webview")
                )
            }
        }

        // AMOLED Screen Night Filter option removed. Directly rendering standard high-contrast viewport.

        // --- Tabs overlay list panel ---
        AnimatedVisibility(
            visible = showTabsOverlay,
            enter = fadeIn(animationSpec = tween(300)) + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 24.dp)
                ) {
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Tabs Manager",
                            style = MaterialTheme.typography.titleLarge,
                            color = CyanNeon,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showTabsOverlay = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.openNewTab(isPrivate = false) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "Add tab")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Standard Tab")
                        }
                        Button(
                            onClick = { viewModel.openNewTab(isPrivate = true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed.copy(alpha = 0.3f))
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = "Add private tab")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Private Tab", color = DangerRed)
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(tabs) { tab ->
                            val isActive = currentTabId == tab.id
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isActive) CyanNeon.copy(alpha = 0.15f) else DarkSurface)
                                    .border(
                                        width = if (isActive) 2.dp else 0.5.dp,
                                        color = if (isActive) CyanNeon else Color.Gray.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        viewModel.selectTab(tab.id)
                                        showTabsOverlay = false
                                    }
                                    .padding(10.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (tab.isPrivate) {
                                                Icon(
                                                    Icons.Default.VisibilityOff,
                                                    contentDescription = "Private",
                                                    tint = DangerRed,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                            }
                                            Text(
                                                text = if (tab.isPinned) "📌 Pinned" else "Tab",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.LightGray
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.closeTab(tab) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Close Tab", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = tab.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = tab.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    // Pinned toggles
                                    TextButton(
                                        onClick = { viewModel.toggleTabPinned(tab) },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(
                                            text = if (tab.isPinned) "Unpin" else "Pin",
                                            fontSize = 11.sp,
                                            color = CyanNeon
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- Security & Transparency Dashboard bottom sheet ---
        if (showDashboardSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDashboardSheet = false },
                containerColor = DarkSurface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                DashboardSheetContent(
                    viewModel = viewModel,
                    onDismiss = { showDashboardSheet = false },
                    onAnalyzePrivacy = { showAiAssistantDialog = true },
                    onInspectResource = { showAiAssistantDialog = true }
                )
            }
        }

        // --- Quick Menu options drawer sheet ---
        if (showSettingsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showSettingsSheet = false },
                containerColor = DarkSurface
            ) {
                SettingsMenuSheetContent(
                    viewModel = viewModel,
                    onDismiss = { showSettingsSheet = false },
                    onBookmarksClick = { showBookmarksDialog = true },
                    onHistoryClick = { showHistoryDialog = true },
                    onScriptsClick = { showScriptsDialog = true },
                    onSourceInspectClick = {
                        viewModel.fetchPageSource()
                        showSourceDialog = true
                    }
                )
            }
        }

        // --- BOOKMARKS DIALOG ---
        if (showBookmarksDialog) {
            val items by viewModel.bookmarks.collectAsStateWithLifecycle()
            AlertDialog(
                onDismissRequest = { showBookmarksDialog = false },
                title = { Text("Saved Bookmarks", color = CyanNeon, fontWeight = FontWeight.Bold) },
                text = {
                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                        if (items.isEmpty()) {
                            Text("No bookmarks saved yet.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        } else {
                            LazyColumn {
                                items(items) { b ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.onUrlEntered(b.url)
                                                showBookmarksDialog = false
                                                showSettingsSheet = false
                                            }
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(b.title, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(b.url, color = Color.Gray, fontSize = 11.sp)
                                        }
                                        IconButton(onClick = { viewModel.removeBookmark(b.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = DangerRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBookmarksDialog = false }) {
                        Text("Close", color = CyanNeon)
                    }
                },
                containerColor = DarkSurfaceVariant
            )
        }

        // --- HISTORY DIALOG ---
        if (showHistoryDialog) {
            val items by viewModel.history.collectAsStateWithLifecycle()
            AlertDialog(
                onDismissRequest = { showHistoryDialog = false },
                title = { Text("Browsing History", color = CyanNeon, fontWeight = FontWeight.Bold) },
                text = {
                    Box(modifier = Modifier.heightIn(max = 300.dp)) {
                        if (items.isEmpty()) {
                            Text("No recent history.", color = Color.Gray, modifier = Modifier.padding(16.dp))
                        } else {
                            LazyColumn {
                                items(items) { h ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.onUrlEntered(h.url)
                                                showHistoryDialog = false
                                                showSettingsSheet = false
                                            }
                                            .padding(vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(h.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text(h.url, color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                                            // Blocks metadata
                                            Text(
                                                text = "Blocks: 🚫 ${h.adBlockCount + h.trackerBlockCount} ads/trackers | Upgrades: 🔒 ${h.redirectCount > 0}",
                                                color = SecureGreen,
                                                fontSize = 10.sp
                                            )
                                        }
                                        IconButton(onClick = { viewModel.deleteHistoryById(h.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Gray)
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(onClick = { viewModel.clearHistory() }) {
                            Text("Clear All", color = DangerRed)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = { showHistoryDialog = false }) {
                            Text("Close", color = CyanNeon)
                        }
                    }
                },
                containerColor = DarkSurfaceVariant
            )
        }

        // --- BUILD-IN SCRIPTS DIRECTORY DIALOG ---
        if (showScriptsDialog) {
            val items by viewModel.customScripts.collectAsStateWithLifecycle()
            var nameInput by remember { mutableStateOf("") }
            var patternInput by remember { mutableStateOf("") }
            var codeInput by remember { mutableStateOf("") }
            var isCreatingScript by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showScriptsDialog = false },
                title = { Text("Scripts Console manager", color = CyanNeon, fontWeight = FontWeight.Bold) },
                text = {
                    Box(modifier = Modifier.heightIn(max = 350.dp)) {
                        if (isCreatingScript) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = nameInput,
                                    onValueChange = { nameInput = it },
                                    label = { Text("Script title") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = patternInput,
                                    onValueChange = { patternInput = it },
                                    label = { Text("Target Site Pattern (e.g., *google.com* or *)") },
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = codeInput,
                                    onValueChange = { codeInput = it },
                                    label = { Text("Javascript block to inject") },
                                    maxLines = 5
                                )
                                Button(
                                    onClick = {
                                        if (nameInput.isNotEmpty() && codeInput.isNotEmpty()) {
                                            viewModel.addCustomScript(nameInput, patternInput, codeInput)
                                            nameInput = ""
                                            patternInput = ""
                                            codeInput = ""
                                            isCreatingScript = false
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Register Script")
                                }
                                TextButton(onClick = { isCreatingScript = false }) {
                                    Text("Back to list")
                                }
                            }
                        } else {
                            Column {
                                Button(
                                    onClick = { isCreatingScript = true },
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = "Add")
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create custom injected script")
                                }
                                
                                LazyColumn {
                                    items(items) { script ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(script.name, color = Color.White, fontWeight = FontWeight.Bold)
                                                Text(script.targetPattern, color = Color.Gray, fontSize = 11.sp)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Switch(
                                                    checked = script.isEnabled,
                                                    onCheckedChange = { viewModel.toggleScriptEnabled(script) }
                                                )
                                                IconButton(onClick = { viewModel.deleteScript(script.id) }) {
                                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = DangerRed)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showScriptsDialog = false }) {
                        Text("Finish", color = CyanNeon)
                    }
                },
                containerColor = DarkSurfaceVariant
            )
        }

        // --- OPTIONAL AI ASSISTANT RESULTS OVERLAY ---
        if (showAiAssistantDialog) {
            val aiResponse by viewModel.aiResponseText.collectAsStateWithLifecycle()
            val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()

            AlertDialog(
                onDismissRequest = {
                    viewModel.clearAiState()
                    showAiAssistantDialog = false
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = "AI", tint = CyanNeon)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("browser.xyz AI Agent", color = CyanNeon, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (isAiLoading) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = CyanNeon)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Analyzing transparent rules using Gemini...", color = Color.White, textAlign = TextAlign.Center)
                            }
                        } else {
                            Box(modifier = Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                                Text(
                                    text = aiResponse ?: "AI error or no query resolved.",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        TextButton(
                            onClick = {
                                viewModel.clearAiState()
                                showAiAssistantDialog = false
                            }
                        ) {
                            Text("Acknowledge", color = CyanNeon)
                        }
                    }
                },
                containerColor = DarkSurfaceVariant
            )
        }

        // --- PAGE SOURCE SYNTAX-HIGHLIGHT VIEWER ---
        if (showSourceDialog) {
            val srcText by viewModel.pageSource.collectAsStateWithLifecycle()
            val srcLoading by viewModel.isPageSourceLoading.collectAsStateWithLifecycle()

            AlertDialog(
                onDismissRequest = { showSourceDialog = false },
                title = { Text("Inspecting Page HTML Source", color = CyanNeon, fontWeight = FontWeight.Bold) },
                text = {
                    Box(modifier = Modifier.heightIn(max = 350.dp)) {
                        if (srcLoading) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = CyanNeon)
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black).padding(8.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                                Text(
                                    text = srcText,
                                    color = SecureGreen,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSourceDialog = false }) {
                        Text("Close Inspector", color = CyanNeon)
                    }
                },
                containerColor = DarkSurfaceVariant
            )
        }

        // Floating Secure One UI Galaxy FAB Companion
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 100.dp, end = 16.dp),
            contentAlignment = Alignment.BottomEnd
        ) {
            ExtendedFloatingActionButton(
                onClick = { showAiShoppingSheet = true },
                containerColor = CyanNeon,
                contentColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.testTag("ai_shopping_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "AI Companion",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("AI Shopping", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Smart Assistant Modal Sheets
        if (showAiShoppingSheet) {
            AiShoppingBottomSheet(
                viewModel = viewModel,
                onDismiss = { showAiShoppingSheet = false },
                onCheckoutClick = {
                    showAiShoppingSheet = false
                    showBiometricSimulator = true
                }
            )
        }

        if (showBiometricSimulator) {
            BiometricPaySimulatorDialog(
                viewModel = viewModel,
                onDismiss = { showBiometricSimulator = false }
            )
        }
    }
}

// --- CONTEXT SENSITIVE SHIELD/TRANSPARENCY DASHBOARD CONTENT ---
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardSheetContent(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    onAnalyzePrivacy: () -> Unit,
    onInspectResource: () -> Unit
) {
    val score by viewModel.privacyScore.collectAsStateWithLifecycle()
    val isSecure by viewModel.isHttpsSecure.collectAsStateWithLifecycle()
    val adBlocks by viewModel.adBlockCount.collectAsStateWithLifecycle()
    val trackerBlocks by viewModel.trackerBlockCount.collectAsStateWithLifecycle()
    val cookieBlocks by viewModel.cookieBlockCount.collectAsStateWithLifecycle()
    val fpBlocks by viewModel.fingerprintBlockCount.collectAsStateWithLifecycle()
    val redirectChain by viewModel.redirectChain.collectAsStateWithLifecycle()
    val resourceLogs by viewModel.resourceLogs.collectAsStateWithLifecycle()

    var activeTabIdx by remember { mutableStateOf(0) } // 0: Overviews, 1: Redirect chains, 2: Resources

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Transparency Panel",
                    style = MaterialTheme.typography.titleLarge,
                    color = CyanNeon,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Real-time background security logs",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.LightGray
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Large Premium Arc/Circle Privacy Score indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Safety Score: $score/100",
                    fontWeight = FontWeight.Bold,
                    color = if (score > 80) SecureGreen else if (score > 50) WarningYellow else DangerRed
                )
                Text(
                    text = when {
                        !isSecure -> "Caution: Page connection is unencrypted HTTP."
                        score > 85 -> "Outstanding background privacy status."
                        else -> "Risk profile mitigated by ad-tracker blocking"
                    },
                    fontSize = 11.sp,
                    color = Color.LightGray
                )
            }

            // Prompt Gemini privacy audit tips
            Button(
                onClick = {
                    viewModel.runPrivacyAnalysisTask()
                    onAnalyzePrivacy()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = "AI", modifier = Modifier.size(16.dp), tint = Color.Black)
                Spacer(modifier = Modifier.width(4.dp))
                Text("AI privacy audit", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tabs to navigate Dashboard categories
        TabRow(
            selectedTabIndex = activeTabIdx,
            containerColor = Color.Transparent,
            contentColor = CyanNeon
        ) {
            Tab(selected = activeTabIdx == 0, onClick = { activeTabIdx = 0 }) {
                Text("Stats Summary", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = activeTabIdx == 1, onClick = { activeTabIdx = 1 }) {
                Text("Redirect hops (${redirectChain.size})", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = activeTabIdx == 2, onClick = { activeTabIdx = 2 }) {
                Text("Elements logged (${resourceLogs.size})", modifier = Modifier.padding(vertical = 12.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tab views swap
        Box(modifier = Modifier.weight(1f)) {
            when (activeTabIdx) {
                0 -> {
                    // Block Stats Dashboard panel
                    Column(
                        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StatCard(title = "Advertisements Blocked", value = "$adBlocks", statusIcon = Icons.Default.Block, color = DangerRed)
                        StatCard(title = "Social Trackers Blocked", value = "$trackerBlocks", statusIcon = Icons.Default.Security, color = WarningYellow)
                        StatCard(title = "Cookies Isolated", value = "$cookieBlocks", statusIcon = Icons.Default.Info, color = Purple80)
                        StatCard(title = "Fingerprint scripts Mitigated", value = "$fpBlocks", statusIcon = Icons.Default.Fingerprint, color = CyanNeon)
                    }
                }
                1 -> {
                    // Redirect Chain Logs explorer panel
                    if (redirectChain.isEmpty()) {
                        Text("No redirects detected on this page.", color = Color.Gray, modifier = Modifier.padding(24.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(redirectChain) { step ->
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(DarkSurfaceVariant)
                                        .border(
                                            width = if (step.isSuspicious) 1.dp else 0.dp,
                                            color = if (step.isSuspicious) WarningYellow else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Hop Type: ${step.type}",
                                                color = if (step.isSuspicious) WarningYellow else CyanNeon,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            if (step.isSuspicious) {
                                                Icon(
                                                    Icons.Default.Warning,
                                                    contentDescription = "Warning Suspicious",
                                                    tint = WarningYellow,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(step.url, color = Color.White, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(step.explanation, color = Color.LightGray, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Loaded Resource logging panel (Ad and tracker blocks analyzer)
                    if (resourceLogs.isEmpty()) {
                        Text("No assets intercepted yet.", color = Color.Gray, modifier = Modifier.padding(24.dp))
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(resourceLogs) { res ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (res.isBlocked) DangerRed.copy(alpha = 0.1f) else DarkSurfaceVariant)
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (res.isBlocked) DangerRed else SecureGreen)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = res.url,
                                            color = if (res.isBlocked) Color.Red else Color.White,
                                            textDecoration = if (res.isBlocked) TextDecoration.LineThrough else TextDecoration.None,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text("Type: ${res.type}", color = Color.Gray, fontSize = 10.sp)
                                            Text("Method: ${res.method}", color = Color.Gray, fontSize = 10.sp)
                                            Text("Latency: ${res.latencyMs}ms", color = Color.Gray, fontSize = 10.sp)
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    // Trigger Gemini explain block reasons
                                    if (res.isBlocked) {
                                        TextButton(
                                            onClick = {
                                                viewModel.runExplainBlockTask(res.url, res.type.name)
                                                onInspectResource()
                                            },
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text("AI Explain", fontSize = 10.sp, color = CyanNeon)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Stats UI Display card
@Composable
fun StatCard(title: String, value: String, statusIcon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant)
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(statusIcon, contentDescription = title, tint = color, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

// --- DRAG AND SWITCH CONFIGURABLE OPTIONS LIST PANELS ---
@Composable
fun SettingsMenuSheetContent(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    onBookmarksClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onScriptsClick: () -> Unit,
    onSourceInspectClick: () -> Unit
) {
    val isAbEnabled by viewModel.isAdBlockEnabled.collectAsStateWithLifecycle()
    val isCbEnabled by viewModel.isCookieBlockEnabled.collectAsStateWithLifecycle()
    val isJsEnabled by viewModel.isJsEnabled.collectAsStateWithLifecycle()
    val isFpEnabled by viewModel.isFingerprintShieldEnabled.collectAsStateWithLifecycle()
    val isHttpsEnf by viewModel.isHttpsEnforced.collectAsStateWithLifecycle()
    val isDesktop by viewModel.isDesktopMode.collectAsStateWithLifecycle()

    val vpnOn by viewModel.vpnEnabled.collectAsStateWithLifecycle()
    val vpnServer by viewModel.vpnServerName.collectAsStateWithLifecycle()
    val vpnProtocol by viewModel.vpnProtocol.collectAsStateWithLifecycle()
    val vpnBytes by viewModel.vpnBytesTransferred.collectAsStateWithLifecycle()
    val vpnIp by viewModel.vpnServerIp.collectAsStateWithLifecycle()
    val vpnLatency by viewModel.vpnServerLatency.collectAsStateWithLifecycle()

    val customProxyOn by viewModel.customProxyEnabled.collectAsStateWithLifecycle()
    val customProxyHost by viewModel.customProxyHost.collectAsStateWithLifecycle()
    val customProxyPort by viewModel.customProxyPort.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.85f)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Console & Settings",
                style = MaterialTheme.typography.titleLarge,
                color = CyanNeon,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }

        // Secure Privacy Tunnel (VPN & SOCKS5/HTTP Proxy) Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(DarkSurfaceVariant)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VpnLock,
                        contentDescription = "VPN Tunnel",
                        tint = if (vpnOn) CyanNeon else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Secure VPN / Privacy Tunnel", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = if (vpnOn) "Traffic encrypted: Yes" else "Direct connection (ISP exposed)",
                            fontSize = 11.sp,
                            color = if (vpnOn) SecureGreen else Color.Gray
                        )
                    }
                }
                Switch(
                    checked = vpnOn,
                    onCheckedChange = {
                        viewModel.vpnEnabled.value = it
                        if (it) {
                            viewModel.customProxyEnabled.value = false
                        }
                    }
                )
            }

            if (vpnOn) {
                Spacer(modifier = Modifier.height(12.dp))
                
                // Show VPN Metadata Panel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Active Node IP:", fontSize = 11.sp, color = Color.Gray)
                            Text(vpnIp, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Protocol:", fontSize = 11.sp, color = Color.Gray)
                            Text(vpnProtocol, fontSize = 11.sp, color = CyanNeon, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Node Latency:", fontSize = 11.sp, color = Color.Gray)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (vpnLatency < 30) SecureGreen else WarningYellow)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("${vpnLatency} ms", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        
                        // Byte counting formatter
                        val formattedBytes = remember(vpnBytes) {
                            when {
                                vpnBytes > 1024 * 1024 -> String.format("%.2f MB", vpnBytes.toDouble() / (1024 * 1024))
                                vpnBytes > 1024 -> String.format("%.2f KB", vpnBytes.toDouble() / 1024)
                                else -> "$vpnBytes B"
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Secured Bandwidth:", fontSize = 11.sp, color = Color.Gray)
                            Text(formattedBytes, fontSize = 11.sp, color = SecureGreen, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Select Secure Privacy Node Country / Core Location", fontSize = 11.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(6.dp))
                
                // Selected Node Dropdown List Simulator (Fully Custom Scrollable Row of Nodes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.vpnServers.forEach { server ->
                        val isSelected = vpnServer == server.name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CyanNeon.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f))
                                .border(
                                    width = 1.2.dp,
                                    color = if (isSelected) CyanNeon else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    viewModel.vpnServerName.value = server.name
                                    viewModel.vpnServerIp.value = server.ip
                                    viewModel.vpnServerLatency.value = server.latency
                                    viewModel.vpnServerCountryCode.value = server.countryCode
                                    viewModel.vpnProtocol.value = "WireGuard Over SSL (${server.countryCode})"
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(server.icon, fontSize = 13.sp)
                                Text(server.name.substringBefore(" -"), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color.Gray.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(10.dp))

            // Custom proxy section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = "Custom Proxy",
                        tint = if (customProxyOn) CyanNeon else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Custom Private Proxy (SOCKS/HTTP)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                        Text("Manually enter custom relay properties", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                Switch(
                    checked = customProxyOn,
                    onCheckedChange = {
                        viewModel.customProxyEnabled.value = it
                        if (it) {
                            viewModel.vpnEnabled.value = false
                        }
                    }
                )
            }

            if (customProxyOn) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = customProxyHost,
                        onValueChange = { viewModel.customProxyHost.value = it },
                        label = { Text("Proxy Server Host (IP)", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = CyanNeon,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(2f)
                    )
                    OutlinedTextField(
                        value = customProxyPort,
                        onValueChange = { viewModel.customProxyPort.value = it },
                        label = { Text("Proxy Port", fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color.Gray,
                            focusedLabelColor = CyanNeon,
                            unfocusedLabelColor = Color.Gray
                        ),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color.Gray.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(10.dp))

            // Proxyium Safe Web Proxy Section
            val proxyiumOn by viewModel.proxyiumEnabled.collectAsStateWithLifecycle()
            val proxyiumCountrySelected by viewModel.proxyiumCountry.collectAsStateWithLifecycle()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "Proxyium Web Proxy",
                        tint = if (proxyiumOn) CyanNeon else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Proxyium Safe Proxy", fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = if (proxyiumOn) "Routing browsing via Proxyium [$proxyiumCountrySelected]" else "Proxyium Web routing disabled",
                            fontSize = 11.sp,
                            color = if (proxyiumOn) SecureGreen else Color.Gray
                        )
                    }
                }
                Switch(
                    checked = proxyiumOn,
                    onCheckedChange = {
                        viewModel.proxyiumEnabled.value = it
                        if (it) {
                            viewModel.vpnEnabled.value = false
                            viewModel.customProxyEnabled.value = false
                            // Load Proxyium web portal directly for direct browsing
                            viewModel.onUrlEntered("https://proxyium.com/")
                        }
                    }
                )
            }

            if (proxyiumOn) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("Select Proxyium Relay Node:", fontSize = 11.sp, color = Color.LightGray)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val proxyiumNodes = listOf(
                        Pair("US", "🇺🇸 United States"),
                        Pair("PL", "🇵🇱 Poland"),
                        Pair("DE", "🇩🇪 Germany"),
                        Pair("FR", "🇫🇷 France"),
                        Pair("SG", "🇸🇬 Singapore")
                    )
                    proxyiumNodes.forEach { node ->
                        val isSel = proxyiumCountrySelected == node.first
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) CyanNeon.copy(alpha = 0.2f) else Color.Gray.copy(alpha = 0.1f))
                                .border(
                                    width = 1.2.dp,
                                    color = if (isSel) CyanNeon else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    viewModel.proxyiumCountry.value = node.first
                                    viewModel.onUrlEntered("https://proxyium.com/")
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(node.second, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        Divider(color = Color.Gray.copy(alpha = 0.2f))

        // Advanced Switches
        OptionRowSwitch(
            title = "Strict Ad Blocking",
            sub = "Filters advertisements, banners, pop-ups completely.",
            checked = isAbEnabled,
            icon = Icons.Default.Block,
            onCheckedChange = { viewModel.isAdBlockEnabled.value = it }
        )

        OptionRowSwitch(
            title = "Third-Party Cookie Shield",
            sub = "Isolates storage and blocks tracking pixels.",
            checked = isCbEnabled,
            icon = Icons.Default.Info,
            onCheckedChange = { viewModel.isCookieBlockEnabled.value = it }
        )

        OptionRowSwitch(
            title = "Javascript Engine",
            sub = "Toggle execution of scripts per site.",
            checked = isJsEnabled,
            icon = Icons.Default.Code,
            onCheckedChange = { viewModel.isJsEnabled.value = it }
        )

        OptionRowSwitch(
            title = "Anti-Fingerprint Shield",
            sub = "Shield canvas headers and telemetry endpoints.",
            checked = isFpEnabled,
            icon = Icons.Default.Fingerprint,
            onCheckedChange = { viewModel.isFingerprintShieldEnabled.value = it }
        )

        OptionRowSwitch(
            title = "Enforce HTTPS Connection",
            sub = "Block insecure http requests globally.",
            checked = isHttpsEnf,
            icon = Icons.Default.Https,
            onCheckedChange = { viewModel.isHttpsEnforced.value = it }
        )

        OptionRowSwitch(
            title = "Desktop Rendering Mode",
            sub = "Request regular full browser webpages.",
            checked = isDesktop,
            icon = Icons.Default.Settings,
            onCheckedChange = { viewModel.isDesktopMode.value = it }
        )

        Divider(color = Color.Gray.copy(alpha = 0.2f))

        // Trigger dialogues
        Button(
            onClick = {
                onDismiss()
                onBookmarksClick()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
        ) {
            Icon(Icons.Default.Book, contentDescription = "Bookmarks", tint = CyanNeon)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Show saved Bookmarks", color = Color.White)
        }

        Button(
            onClick = {
                onDismiss()
                onHistoryClick()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
        ) {
            Icon(Icons.Default.History, contentDescription = "History", tint = CyanNeon)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage Visited History", color = Color.White)
        }

        Button(
            onClick = {
                onDismiss()
                onScriptsClick()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
        ) {
            Icon(Icons.Default.Code, contentDescription = "Scripts", tint = CyanNeon)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Script Manager Console", color = Color.White)
        }

        Button(
            onClick = {
                onDismiss()
                onSourceInspectClick()
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceVariant)
        ) {
            Icon(Icons.Default.Code, contentDescription = "HTML View", tint = CyanNeon)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Inspect HTML Page Source", color = Color.White)
        }
    }
}

@Composable
fun OptionRowSwitch(
    title: String,
    sub: String,
    checked: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurfaceVariant)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = CyanNeon, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(sub, color = Color.LightGray, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = CyanNeon)
        )
    }
}

// --- SAMSUNG ONE UI STYLE AI SHOPPING COMPANION ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiShoppingBottomSheet(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit,
    onCheckoutClick: () -> Unit
) {
    val logs by viewModel.aiAgentShoppingLogs.collectAsStateWithLifecycle()
    val status by viewModel.aiAgentShoppingStatus.collectAsStateWithLifecycle()
    val products by viewModel.aiAgentBrowsedProducts.collectAsStateWithLifecycle()
    val cart by viewModel.smartCartItems.collectAsStateWithLifecycle()
    var inputQuery by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.92f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(Color.Gray.copy(alpha = 0.3f))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = "Galaxy Sparkle",
                            tint = CyanNeon,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Smart Companion",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "Autonomous One Agent Browsing",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray.copy(alpha = 0.8f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "What product should the agent find?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inputQuery,
                        onValueChange = { inputQuery = it },
                        placeholder = { Text("e.g. Mechanical keyboard under $90", color = Color.Gray, fontSize = 13.sp) },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanNeon,
                            unfocusedBorderColor = Color.Gray.copy(alpha = 0.4f),
                            focusedContainerColor = DarkSurface,
                            unfocusedContainerColor = DarkSurface
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("ai_shopping_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Sony headphones", "Nike shoes", "Samsung Galaxy", "Keyboards").forEach { suggest ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable { inputQuery = suggest }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(suggest, color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (inputQuery.isNotEmpty()) {
                                viewModel.runAiShoppingAgent(inputQuery)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("launch_ai_agent_button")
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Launch")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Search & Auto-Browse", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (status != "Idle") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (status == "Searching" || status == "Analyzing") {
                                    CircularProgressIndicator(
                                        color = CyanNeon,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = "Done", tint = SecureGreen, modifier = Modifier.size(18.dp))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Agent Status: $status",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                            Text(
                                text = "${logs.size} tracelogs",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            logs.forEach { log ->
                                Text(
                                    text = log,
                                    color = if (log.contains("[Completed]") || log.contains("Parsed")) SecureGreen else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (status == "Completed" && products.isNotEmpty()) {
                Text(
                    text = "Parsed Matches from Web View:",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    products.forEach { prod ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = prod.icon,
                                            fontSize = 28.sp,
                                            modifier = Modifier.padding(end = 12.dp)
                                        )
                                        Column {
                                            Text(
                                                text = prod.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Brand: ${prod.brand}",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Text(
                                        text = prod.formattedPrice,
                                        color = CyanNeon,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = prod.specs,
                                    color = Color.LightGray.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Star, contentDescription = "Stars", tint = WarningYellow, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        val ratingLabel = if (prod.price > 0.0) "customer reviews" else "views / rating"
                                        Text("${prod.rating} (${prod.reviewCount} $ratingLabel)", color = Color.Gray, fontSize = 11.sp)
                                    }

                                    if (prod.price > 0.0) {
                                        Button(
                                            onClick = { viewModel.addToSmartCart(prod) },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Rounded.AddShoppingCart, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add to Cart", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                viewModel.onUrlEntered(prod.url)
                                                onDismiss()
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Rounded.Language, contentDescription = "Go", tint = Color.White, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Open Page", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (cart.isNotEmpty()) {
                val totalCost = remember(cart) { cart.sumOf { it.product.price * it.quantity } }
                Card(
                    colors = CardDefaults.cardColors(containerColor = CyanNeon),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCheckoutClick() }
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.ShoppingCart, contentDescription = "Cart", tint = Color.White, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Smart Unified Cart", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("${cart.sumOf { it.quantity }} items collected", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = String.format("$%.2f", totalCost),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun BiometricPaySimulatorDialog(
    viewModel: BrowserViewModel,
    onDismiss: () -> Unit
) {
    val cart by viewModel.smartCartItems.collectAsStateWithLifecycle()
    val totalCost = remember(cart) { cart.sumOf { it.product.price * it.quantity } }
    var payStep by remember { mutableStateOf(0) } // 0: Review, 1: Touch ID, 2: Receipt Receipt
    var heldProgress by remember { mutableStateOf(0f) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        containerColor = DarkSurface,
        shape = RoundedCornerShape(28.dp),
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp)
            ) {
                if (payStep == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cart Review", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            cart.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(DarkSurfaceVariant)
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.product.icon, fontSize = 22.sp, modifier = Modifier.padding(end = 8.dp))
                                        Column {
                                            Text(item.product.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                            Text("Qty: ${item.quantity} x ${item.product.formattedPrice}", color = Color.Gray, fontSize = 11.sp)
                                        }
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeFromSmartCart(item.product.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = DangerRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Order Total:", color = Color.LightGray, fontSize = 13.sp)
                        Text(String.format("$%.2f", totalCost), color = CyanNeon, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { payStep = 1 },
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = "Secure Pay", tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Pay via One UI SecurePay", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                } else if (payStep == 1) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Biometric Pass Wallet",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please touch and hold the sensor below to authorize payment of " + String.format("$%.2f", totalCost),
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        val infiniteTransition = rememberInfiniteTransition()
                        val scaleOuter by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.4f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )
                        val alphaOuter by infiniteTransition.animateFloat(
                            initialValue = 0.6f,
                            targetValue = 0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            )
                        )

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(140.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .scale(scaleOuter)
                                    .border(2.dp, CyanNeon.copy(alpha = alphaOuter), CircleShape)
                            )
                            
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(CyanNeon, CyanNeon.copy(alpha = 0.7f), Color.Transparent)
                                        )
                                    )
                                    .clickable {
                                        scope.launch {
                                            heldProgress = 0f
                                            while (heldProgress < 1f) {
                                                kotlinx.coroutines.delay(80)
                                                heldProgress += 0.1f
                                            }
                                            payStep = 2
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Fingerprint,
                                    contentDescription = "Tap to scan fingerprint",
                                    tint = Color.White,
                                    modifier = Modifier.size(42.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "HOLD SENSOR TO AUTHORIZE",
                            color = CyanNeon.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { heldProgress },
                            color = CyanNeon,
                            trackColor = Color.White.copy(alpha = 0.1f),
                            modifier = Modifier.width(160.dp).height(4.dp).clip(CircleShape)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verified Receipt",
                            tint = SecureGreen,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Transaction authorized successfully!",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Authorized via Proxyium and dispatched securely through One Wallet token.",
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Order ref:", fontSize = 10.sp, color = Color.Gray)
                                    Text("#ONE-PAY-SECURE", fontSize = 10.sp, color = Color.White, fontFamily = FontFamily.Monospace)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Routing Tunnel:", fontSize = 10.sp, color = Color.Gray)
                                    Text("Proxyium Safe Node SSL", fontSize = 10.sp, color = SecureGreen)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Pay tokens dispatched:", fontSize = 10.sp, color = Color.Gray)
                                    Text("${cart.sumOf { it.quantity }} items synced", fontSize = 10.sp, color = Color.White)
                                }
                                Divider(color = Color.White.copy(alpha = 0.05f), modifier = Modifier.padding(vertical = 4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Amount Transacted:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                                    Text(String.format("$%.2f", totalCost), fontSize = 14.sp, color = CyanNeon, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                viewModel.clearSmartCart()
                                onDismiss()
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanNeon),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("Acknowledge & Clear", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    )
}
