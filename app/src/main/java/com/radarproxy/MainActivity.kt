package com.radarproxy

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.radarproxy.core.ping.ActiveNetworkProvider
import com.radarproxy.core.ping.ProxyPingEngine
import com.radarproxy.core.telegram.TelegramLinkBuilder
import com.radarproxy.data.local.*
import com.radarproxy.data.repository.*
import com.radarproxy.domain.*
import com.radarproxy.worker.ProxyCleanupWorker
import com.radarproxy.worker.ProxyUpdateWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

private const val OFFICIAL_URL = "https://raw.githubusercontent.com/khodejav/telegram-proxy-collector/main/output/proxies.txt"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val database = Room.databaseBuilder(applicationContext, ProxyDatabase::class.java, "radar_proxy.db").build()
        val repository = ProxyRepository(database)
        val settingsStore = SettingsStore(applicationContext)
        setContent { RadarProxyApp(repository, settingsStore) }
        lifecycleScope.launch(Dispatchers.IO) {
            database.sources().upsert(SourceEntity("official", OFFICIAL_URL, true, true, null, null, 0, null))
            val current = settingsStore.flow.first()
            ProxyUpdateWorker.schedule(applicationContext, current.intervalMinutes, current.wifiOnly, current.autoUpdateEnabled)
            ProxyCleanupWorker.schedule(applicationContext, current.autoDeleteEnabled, current.autoDeleteHours)
            if (current.autoUpdateEnabled) repository.refreshAll()
        }
    }
}

@Composable
private fun RadarProxyApp(repository: ProxyRepository, settingsStore: SettingsStore) {
    val context = LocalContext.current
    val fallbackScope = rememberCoroutineScope()
    val scope = (context as? ComponentActivity)?.lifecycleScope ?: fallbackScope
    val networkProvider = remember { ActiveNetworkProvider(context) }
    var settings by remember { mutableStateOf<Settings?>(null) }
    LaunchedEffect(Unit) { settingsStore.flow.collectLatest { settings = it } }
    val settingsValue = settings ?: return@RadarProxyApp
    var screen by rememberSaveable { mutableStateOf("home") }
    var tab by rememberSaveable { mutableStateOf(ProxyType.MT_PROTO) }
    var sort by rememberSaveable { mutableStateOf(SortMode.DEFAULT) }
    var proxies by remember { mutableStateOf<List<ProxyEntity>>(emptyList()) }
    var sources by remember { mutableStateOf<List<SourceEntity>>(emptyList()) }
    var testingAll by remember { mutableStateOf(false) }
    var testProgress by rememberSaveable { mutableStateOf(0) }
    var testTotal by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(Unit) { repository.proxies.collectLatest { proxies = it } }
    LaunchedEffect(Unit) { repository.sources.collectLatest { sources = it } }
    LaunchedEffect(settingsValue.intervalMinutes, settingsValue.wifiOnly, settingsValue.autoUpdateEnabled) { ProxyUpdateWorker.schedule(context, settingsValue.intervalMinutes, settingsValue.wifiOnly, settingsValue.autoUpdateEnabled) }
    LaunchedEffect(settingsValue.autoDeleteEnabled, settingsValue.autoDeleteHours) { ProxyCleanupWorker.schedule(context, settingsValue.autoDeleteEnabled, settingsValue.autoDeleteHours) }
    val lastUpdated = sources.mapNotNull { it.lastUpdated }.maxOrNull()

    val fa = settingsValue.language == AppLanguage.PERSIAN
    val baseDensity = LocalDensity.current
    val textDensity = Density(baseDensity.density, baseDensity.fontScale * settingsValue.textSize.fontScale)
    val colors = when (settingsValue.theme) {
        AppTheme.LIGHT -> lightColorScheme(primary = Color(0xFF1677FF), background = Color(0xFFF4F6FA), surface = Color.White, onSurface = Color(0xFF172033))
        AppTheme.DARK -> darkColorScheme(primary = Color(0xFF79AFFF), background = Color(0xFF10141B), surface = Color(0xFF1A202A))
        AppTheme.AURA -> darkColorScheme(primary = Color(0xFFB08CFF), background = Color(0xFF17142A), surface = Color(0xFF282141))
        AppTheme.DISCORD -> darkColorScheme(primary = Color(0xFF9CA8FF), background = Color(0xFF18191C), surface = Color(0xFF2B2D31))
    }
    ApplySystemBars(colors.background, settingsValue.theme == AppTheme.LIGHT)

    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalDensity provides textDensity
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = colors.background,
                bottomBar = {
                    Box(Modifier.fillMaxWidth().background(colors.surface).windowInsetsPadding(WindowInsets.navigationBars)) {
                        NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0), containerColor = Color.Transparent, tonalElevation = 0.dp) {
                        BottomItem(screen == "home", { screen = "home" }, Icons.Default.Home, if (fa) "خانه" else "Home")
                        BottomItem(screen == "sources", { screen = "sources" }, Icons.Default.Link, if (fa) "منابع" else "Sources")
                            BottomItem(screen == "settings", { screen = "settings" }, Icons.Default.Settings, if (fa) "تنظیمات" else "Settings")
                        }
                    }
                }
            ) { padding ->
                val safeContent = Modifier.padding(padding).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                when (screen) {
                    "sources" -> SourcesScreen(repository, sources, fa, safeContent)
                    "settings" -> SettingsScreen(settingsValue, settingsStore, fa, safeContent)
                    else -> HomeScreen(
                        raw = proxies, lastUpdated = lastUpdated, tab = tab, setTab = { tab = it }, sort = sort, setSort = { sort = it },
                        testingAll = testingAll,
                        testProgress = testProgress,
                        testTotal = testTotal,
                        testAll = {
                            if (!testingAll) {
                                val target = proxies.toList()
                                scope.launch {
                                    testingAll = true
                                    testProgress = 0
                                    testTotal = target.size
                                    try {
                                        withContext(Dispatchers.IO) {
                                            supervisorScope {
                                                val cursor = AtomicInteger(0)
                                                val completed = AtomicInteger(0)
                                                val workers = List(minOf(4, target.size)) {
                                                    launch {
                                                        while (true) {
                                                            val index = cursor.getAndIncrement()
                                                            if (index >= target.size) break
                                                            val entity = target[index]
                                                            try {
                                                                val p = entity.toDomain()
                                                                repository.setPing(p.id, null, PingStatus.TESTING)
                                                                val result = ProxyPingEngine(networkProvider).test(p, 10_000)
                                                                result.fold(
                                                                    { ms -> repository.setPing(p.id, ms, statusFor(ms)) },
                                                                    { error -> repository.setPing(p.id, null, statusFor(error)) }
                                                                )
                                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                                throw cancelled
                                                            } catch (_: Throwable) {
                                                                runCatching { repository.setPing(entity.id, null, PingStatus.TIMEOUT) }
                                                            } finally {
                                                                val done = completed.incrementAndGet()
                                                                withContext(Dispatchers.Main.immediate) { testProgress = done }
                                                            }
                                                        }
                                                    }
                                                }
                                                workers.joinAll()
                                            }
                                        }
                                    } finally {
                                        testingAll = false
                                    }
                                }
                            }
                        },
                        ping = { entity ->
                            if (!testingAll) scope.launch {
                                val p = entity.toDomain()
                                repository.setPing(p.id, null, PingStatus.TESTING)
                                val result = ProxyPingEngine(networkProvider).test(p, 10_000)
                                result.fold(
                                    { ms -> repository.setPing(p.id, ms, statusFor(ms)) },
                                    { error -> repository.setPing(p.id, null, statusFor(error)) }
                                )
                            }
                        },
                        copyProxy = { entity ->
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                            clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("Proxy", TelegramLinkBuilder.buildUrl(entity.toDomain())))
                            Toast.makeText(context, if (fa) "پروکسی کپی شد" else "Proxy copied", Toast.LENGTH_SHORT).show()
                        },
                        connect = { entity ->
                            runCatching {
                                val telegramIntent = Intent(Intent.ACTION_VIEW, TelegramLinkBuilder.build(entity.toDomain()))
                                context.startActivity(Intent.createChooser(telegramIntent, if (fa) "انتخاب برنامه برای باز کردن" else "Open with"))
                            }.onFailure { Toast.makeText(context, if (fa) "تلگرام نصب نیست" else "Telegram is not installed", Toast.LENGTH_LONG).show() }
                        },
                        fa = fa,
                        showCategoryControls = settingsValue.showCategoryControls,
                        setShowCategoryControls = { value -> scope.launch { settingsStore.showCategoryControls(value) } },
                        showSortControl = settingsValue.showSortControl,
                        setShowSortControl = { value -> scope.launch { settingsStore.showSortControl(value) } },
                        showTestAllControl = settingsValue.showTestAllControl,
                        setShowTestAllControl = { value -> scope.launch { settingsStore.showTestAllControl(value) } },
                        modifier = safeContent
                    )
                }
            }
        }
    }
}

@Composable
private fun ApplySystemBars(background: Color, lightIcons: Boolean) {
    val context = LocalContext.current
    SideEffect {
        val activity = context as? Activity ?: return@SideEffect
        val window = activity.window
        window.decorView.setBackgroundColor(background.toArgb())
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightIcons
    }
}

private fun statusFor(@Suppress("UNUSED_PARAMETER") ms: Long) = PingStatus.ONLINE

private fun isTargetResponsiveDensity(context: Context): Boolean = context.resources.displayMetrics.densityDpi in 390..480

private fun statusFor(error: Throwable): PingStatus = when ((error as? ProxyPingEngine.PingFailure)?.kind) {
    ProxyPingEngine.FailureKind.TIMEOUT, ProxyPingEngine.FailureKind.TRANSIENT -> PingStatus.TIMEOUT
    ProxyPingEngine.FailureKind.INVALID, ProxyPingEngine.FailureKind.PROTOCOL, null -> PingStatus.TIMEOUT
}

private fun receivedAgeLabel(firstSeen: Long, now: Long = System.currentTimeMillis(), fa: Boolean): String {
    val ageMillis = (now - firstSeen).coerceAtLeast(0L)
    val hours = ageMillis / 3_600_000L
    return when {
        hours <= 0L -> if (fa) "اکنون" else "just now"
        fa -> "$hours ساعت پیش"
        else -> "${hours}h ago"
    }
}

private fun formatPersianDateTime(epochMillis: Long): String {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
    val jalali = gregorianToJalali(calendar.get(java.util.Calendar.YEAR), calendar.get(java.util.Calendar.MONTH) + 1, calendar.get(java.util.Calendar.DAY_OF_MONTH))
    val value = "%04d/%02d/%02d %02d:%02d".format(java.util.Locale.US, jalali[0], jalali[1], jalali[2], calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))
    return value
}

private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): IntArray {
    val monthDays = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    val gy2 = if (gm > 2) gy + 1 else gy
    var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + monthDays[gm - 1]
    var jy = -1595 + 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    return intArrayOf(jy, jm, jd)
}

private fun ProxyEntity.toDomain() = ProxyEntry(id, ProxyType.valueOf(type), server, port, secret, username, password, sourceId, firstSeen, lastSeen, pingMs, PingStatus.valueOf(pingStatus), lastPingTime)

@Composable
private fun RowScope.BottomItem(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String) {
    NavigationBarItem(selected, onClick, icon = { Icon(icon, label) }, label = { Text(label) })
}

@Composable
private fun HomeScreen(raw: List<ProxyEntity>, lastUpdated: Long?, tab: ProxyType, setTab: (ProxyType) -> Unit, sort: SortMode, setSort: (SortMode) -> Unit, testingAll: Boolean, testProgress: Int, testTotal: Int, testAll: () -> Unit, ping: (ProxyEntity) -> Unit, copyProxy: (ProxyEntity) -> Unit, connect: (ProxyEntity) -> Unit, fa: Boolean, showCategoryControls: Boolean, setShowCategoryControls: (Boolean) -> Unit, showSortControl: Boolean, setShowSortControl: (Boolean) -> Unit, showTestAllControl: Boolean, setShowTestAllControl: (Boolean) -> Unit, modifier: Modifier) {
    var sortMenu by remember { mutableStateOf(false) }
    var controlsMenuExpanded by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    var previousSort by remember { mutableStateOf(sort) }
    LaunchedEffect(sort) {
        if (previousSort != sort) {
            previousSort = sort
            homeListState.scrollToItem(0)
        }
    }
    val densityDpi = LocalContext.current.resources.displayMetrics.densityDpi
    val compactHomeDpi = densityDpi in 390..480
    val largeHomeControls = densityDpi > 600
    val visible = raw.filter { it.type == tab.name }.let { list ->
        when (sort) {
            SortMode.FASTEST -> list.sortedWith(compareBy({ it.pingMs == null }, { it.pingMs ?: Long.MAX_VALUE }))
            SortMode.SLOWEST -> list.sortedWith(compareByDescending<ProxyEntity> { it.pingMs != null }.thenByDescending { it.pingMs ?: Long.MIN_VALUE })
            SortMode.NEWEST -> list.sortedByDescending { it.firstSeen }
            SortMode.OLDEST -> list.sortedBy { it.firstSeen }
            SortMode.DEFAULT -> list.sortedByDescending { it.lastSeen }
        }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 480.dp
        val compactHomeControls = compactHomeDpi && maxWidth <= 480.dp
        val baseHomeDensity = LocalDensity.current
        val homeScale = if (compactHomeControls) (maxWidth.value / 480f).coerceIn(0.8f, 0.88f) else 1f
        val responsiveHomeDensity = Density(baseHomeDensity.density * homeScale, baseHomeDensity.fontScale * homeScale)
        CompositionLocalProvider(LocalDensity provides responsiveHomeDensity) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (compactHomeControls) 12.dp else 18.dp)) {
            Spacer(Modifier.height(if (compactHomeControls) 8.dp else 18.dp))
            if (compact) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Radar Proxy", fontSize = if (compactHomeControls) 22.sp else 28.sp, fontWeight = FontWeight.Bold)
                        Text(if (fa) "مرکز پروکسی تلگرام" else "Telegram proxy radar", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
                    }
                    StatusPill(if (fa) "آماده" else "Ready")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Radar Proxy", fontSize = if (compactHomeControls) 22.sp else 28.sp, fontWeight = FontWeight.Bold)
                        Text(if (fa) "مرکز پروکسی تلگرام" else "Telegram proxy radar", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .56f))
                    }
                    StatusPill(if (fa) "آماده" else "Ready")
                }
            }
            Spacer(Modifier.height(if (compactHomeControls) 8.dp else 18.dp))
            SummaryCard(
                total = raw.size,
                lastUpdated = lastUpdated,
                fa = fa,
                menuExpanded = controlsMenuExpanded,
                onMenuExpandedChange = { controlsMenuExpanded = it },
                showCategoryControls = showCategoryControls,
                showSortControl = showSortControl,
                showTestAllControl = showTestAllControl,
                onCategoryVisibilityChange = setShowCategoryControls,
                onSortVisibilityChange = setShowSortControl,
                onTestAllVisibilityChange = setShowTestAllControl
            )
            if (showCategoryControls || showSortControl || showTestAllControl) {
                Column(Modifier.fillMaxWidth()) {
                    if (showCategoryControls) {
                        Spacer(Modifier.height(if (compactHomeControls) 4.dp else 8.dp))
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surface).padding(4.dp)) {
                            Segment(tab == ProxyType.MT_PROTO, { setTab(ProxyType.MT_PROTO) }, "MT Proto", raw.count { it.type == ProxyType.MT_PROTO.name })
                            Segment(tab == ProxyType.SOCKS5, { setTab(ProxyType.SOCKS5) }, "SOCKS5", raw.count { it.type == ProxyType.SOCKS5.name })
                        }
                    }
                    if (showCategoryControls && (showSortControl || showTestAllControl)) {
                        Spacer(Modifier.height(if (compactHomeControls) 6.dp else 12.dp))
                    }
                    if (showSortControl || showTestAllControl) {
                        if (compactHomeControls || !compact) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                if (showSortControl) {
                                    Text(if (fa) "مرتب‌سازی" else "Sort", fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.width(8.dp))
                                    Box {
                                        AssistChip({ sortMenu = true }, modifier = if (largeHomeControls) Modifier.height(44.dp) else Modifier, label = { if (compactHomeControls) Text(sortLabel(sort, fa), maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) else Text(sortLabel(sort, fa)) })
                                        DropdownMenu(sortMenu, { sortMenu = false }) { SortMode.values().forEach { mode -> DropdownMenuItem({ Text(sortLabel(mode, fa)) }, { setSort(mode); sortMenu = false }) } }
                                    }
                                }
                                if (showTestAllControl) {
                                    Spacer(Modifier.weight(1f))
                                    OutlinedButton(testAll, modifier = if (largeHomeControls) Modifier.height(44.dp) else Modifier, shape = RoundedCornerShape(13.dp), enabled = !testingAll, contentPadding = if (compactHomeControls) PaddingValues(horizontal = 7.dp, vertical = 0.dp) else ButtonDefaults.ContentPadding) {
                                        Icon(if (testingAll) Icons.Default.HourglassTop else Icons.Default.Speed, null, modifier = if (compactHomeControls) Modifier.size(18.dp) else Modifier)
                                        Spacer(Modifier.width(if (compactHomeControls) 3.dp else 5.dp))
                                        if (compactHomeControls) Text(if (testingAll) "${testProgress}/${testTotal}" else (if (fa) "تست همه" else "Test all"), maxLines = 1, softWrap = false) else Text(if (testingAll) "${testProgress}/${testTotal}" else (if (fa) "تست همه" else "Test all"))
                                    }
                                }
                            }
                        } else {
                            Column(Modifier.fillMaxWidth()) {
                                if (showSortControl) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Text(if (fa) "مرتب‌سازی" else "Sort", fontWeight = FontWeight.SemiBold)
                                        Spacer(Modifier.width(8.dp))
                                        Box {
                                            AssistChip({ sortMenu = true }, modifier = if (largeHomeControls) Modifier.height(44.dp) else Modifier, label = { Text(sortLabel(sort, fa)) })
                                            DropdownMenu(sortMenu, { sortMenu = false }) { SortMode.values().forEach { mode -> DropdownMenuItem({ Text(sortLabel(mode, fa)) }, { setSort(mode); sortMenu = false }) } }
                                        }
                                    }
                                }
                                if (showSortControl && showTestAllControl) Spacer(Modifier.height(8.dp))
                                if (showTestAllControl) {
                                    OutlinedButton(testAll, modifier = (if (largeHomeControls) Modifier.height(44.dp) else Modifier).fillMaxWidth(), shape = RoundedCornerShape(13.dp), enabled = !testingAll) {
                                        Icon(if (testingAll) Icons.Default.HourglassTop else Icons.Default.Speed, null)
                                        Spacer(Modifier.width(5.dp))
                                        Text(if (testingAll) "${testProgress}/${testTotal}" else (if (fa) "تست همه" else "Test all"))
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(if (compactHomeControls) 4.dp else 10.dp))
                }
            }
            if (visible.isEmpty()) {
                EmptyState(fa)
            } else {
                LazyColumn(Modifier.weight(1f), state = homeListState, verticalArrangement = Arrangement.spacedBy(if (compactHomeControls) 5.dp else 10.dp), contentPadding = PaddingValues(bottom = if (compactHomeControls) 6.dp else 18.dp)) {
                    items(visible, key = { it.id }) { entity -> ProxyCard(entity, raw.indexOf(entity) + 1, fa, { ping(entity) }, { copyProxy(entity) }, { connect(entity) }) }
                }
            }
        }
        }
    }
}

@Composable private fun RowScope.Segment(selected: Boolean, onClick: () -> Unit, label: String, count: Int) { TextButton(onClick, modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)) { Text("$label  $count", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold) } }
@Composable private fun StatusPill(text: String) { Surface(shape = RoundedCornerShape(50), color = Color(0x1A22A06B)) { Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFF22A06B))); Spacer(Modifier.width(6.dp)); Text(text, color = Color(0xFF18794E), fontWeight = FontWeight.Bold, fontSize = 12.sp) } } }
@Composable
private fun SummaryCard(
    total: Int,
    lastUpdated: Long?,
    fa: Boolean,
    menuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit,
    showCategoryControls: Boolean,
    showSortControl: Boolean,
    showTestAllControl: Boolean,
    onCategoryVisibilityChange: (Boolean) -> Unit,
    onSortVisibilityChange: (Boolean) -> Unit,
    onTestAllVisibilityChange: (Boolean) -> Unit
) {
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth().animateContentSize()) {
            Box(Modifier.background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = .72f))))) {
            Column(Modifier.padding(22.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("TOTAL PROXIES", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Box {
                        IconButton(onClick = { onMenuExpandedChange(true) }) {
                            Icon(Icons.Default.MoreVert, contentDescription = if (fa) "تنظیم نمایش کنترل‌ها" else "Control visibility", tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { onMenuExpandedChange(false) }) {
                            DropdownMenuItem(
                                text = { Text(if (fa) "نمایش MT Proto / SOCKS5" else "Show MT Proto / SOCKS5") },
                                onClick = { onCategoryVisibilityChange(!showCategoryControls) },
                                trailingIcon = { Checkbox(checked = showCategoryControls, onCheckedChange = { onCategoryVisibilityChange(it) }) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (fa) "نمایش مرتب‌سازی" else "Show Sort") },
                                onClick = { onSortVisibilityChange(!showSortControl) },
                                trailingIcon = { Checkbox(checked = showSortControl, onCheckedChange = { onSortVisibilityChange(it) }) }
                            )
                            DropdownMenuItem(
                                text = { Text(if (fa) "نمایش تست همه" else "Show Test All") },
                                onClick = { onTestAllVisibilityChange(!showTestAllControl) },
                                trailingIcon = { Checkbox(checked = showTestAllControl, onCheckedChange = { onTestAllVisibilityChange(it) }) }
                            )
                        }
                    }
                }
                Text(total.toString(), color = MaterialTheme.colorScheme.onPrimary, fontSize = 42.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .2f))
                Spacer(Modifier.height(8.dp))
                Text(if (lastUpdated == null) {
                    if (fa) "آخرین بروزرسانی: در انتظار" else "Last update: waiting"
                } else {
                    if (fa) "آخرین بروزرسانی: ${formatPersianDateTime(lastUpdated)}" else "Last update: ${formatPersianDateTime(lastUpdated)}"
                }, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .9f), fontSize = 12.sp)
            }
        }
    }
}
@Composable private fun ProxyCard(p: ProxyEntity, index: Int, fa: Boolean, ping: () -> Unit, copyProxy: () -> Unit, connect: () -> Unit) {
    Card(shape = RoundedCornerShape(21.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        BoxWithConstraints {
            val compact = maxWidth < 420.dp || (isTargetResponsiveDensity(LocalContext.current) && maxWidth < 560.dp)
            Column(Modifier.padding(if (compact) 10.dp else 17.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.size(if (compact) 30.dp else 36.dp).clip(RoundedCornerShape(if (compact) 10.dp else 12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                        Text(index.toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(if (compact) 7.dp else 11.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (fa) "تلگرام" else "Telegram", fontWeight = FontWeight.Bold, fontSize = if (compact) 14.sp else 16.sp, maxLines = 1)
                            Spacer(Modifier.width(if (compact) 5.dp else 7.dp))
                            Text(receivedAgeLabel(p.firstSeen, fa = fa), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = if (compact) 10.sp else 11.sp, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        Text(if (p.type == ProxyType.MT_PROTO.name) "MT Proto" else "SOCKS5", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = if (compact) 11.sp else 12.sp)
                    }
                    Spacer(Modifier.width(if (compact) 4.dp else 8.dp))
                    PingLabel(p)
                }
                Spacer(Modifier.height(if (compact) 7.dp else 12.dp))
                if (compact) {
                    Column(Modifier.fillMaxWidth()) {
                        InfoCell(if (fa) "سرور" else "SERVER", p.server)
                        Spacer(Modifier.height(5.dp))
                        InfoCell(if (fa) "پورت" else "PORT", p.port.toString())
                    }
                } else {
                    Row(Modifier.fillMaxWidth()) {
                        InfoCell(if (fa) "سرور" else "SERVER", p.server)
                        Spacer(Modifier.width(24.dp))
                        InfoCell(if (fa) "پورت" else "PORT", p.port.toString())
                    }
                }
                Spacer(Modifier.height(if (compact) 8.dp else 14.dp))
                if (compact) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(ping, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Icon(Icons.Default.Speed, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(if (fa) "تست" else "Test", fontSize = 11.sp)
                        }
                        TextButton(copyProxy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(if (fa) "کپی" else "Copy", fontSize = 11.sp)
                        }
                        Button(connect, modifier = Modifier.weight(1.35f), contentPadding = PaddingValues(horizontal = 4.dp), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Link, null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(if (fa) "اتصال" else "Connect", fontSize = 11.sp)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(ping) {
                            Icon(Icons.Default.Speed, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (fa) "تست" else "Test")
                        }
                        TextButton(copyProxy) {
                            Icon(Icons.Default.ContentCopy, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (fa) "کپی" else "Copy")
                        }
                        Button(connect, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Link, null)
                            Spacer(Modifier.width(4.dp))
                            Text(if (fa) "اتصال" else "Connect")
                        }
                    }
                }
            }
        }
    }
}
@Composable private fun InfoCell(title: String, value: String) { Column { Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f), fontWeight = FontWeight.Bold); Text(value, fontWeight = FontWeight.SemiBold) } }
@Composable private fun PingLabel(p: ProxyEntity) { val status = PingStatus.valueOf(p.pingStatus); val color = when { p.pingMs != null -> Color(0xFF1D9A6C); status == PingStatus.TIMEOUT || status == PingStatus.FAILED -> Color(0xFFD64545); status == PingStatus.PROTOCOL_ERROR || status == PingStatus.INVALID_PROXY -> Color(0xFFD64545); status == PingStatus.TESTING -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .5f) }; Text(if (status == PingStatus.TESTING) "…" else p.pingMs?.let { "$it ms" } ?: status.name.lowercase().replace('_', ' '), color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
@Composable private fun EmptyState(fa: Boolean) { Box(Modifier.fillMaxWidth().padding(top = 50.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.CloudOff, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp)); Spacer(Modifier.height(10.dp)); Text(if (fa) "هنوز پروکسی‌ای دریافت نشده است" else "No proxies received yet", fontWeight = FontWeight.Bold); Text(if (fa) "همگام‌سازی خودکار در پس‌زمینه فعال است" else "Automatic background sync is enabled", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f)) } } }

@Composable
private fun SourcesScreen(repo: ProxyRepository, sources: List<SourceEntity>, fa: Boolean, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }
    var updateMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var updateSucceeded by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 480.dp
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            if (compact) {
                Column(Modifier.fillMaxWidth()) {
                    Text(if (fa) "منابع اشتراک" else "Subscriptions", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text(if (fa) "منبع رسمی و منابع سفارشی" else "Official and custom sources", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            if (!updating) scope.launch {
                                updating = true
                                try {
                                    repo.refreshAll().fold(
                                        onSuccess = { count -> updateSucceeded = true; updateMessage = if (fa) "بروزرسانی موفق بود؛ $count پروکسی در فهرست موجود است." else "Update succeeded; $count proxies are available." },
                                        onFailure = { error -> updateSucceeded = false; updateMessage = if (fa) "بروزرسانی ناموفق: ${error.message ?: "خطای ناشناخته"}" else "Update failed: ${error.message ?: "Unknown error"}" }
                                    )
                                } finally { updating = false }
                            }
                        }, modifier = Modifier.fillMaxWidth(), enabled = !updating, shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(if (updating) Icons.Default.HourglassTop else Icons.Default.Sync, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (updating) (if (fa) "در حال بروزرسانی" else "Updating") else "Update Subscription")
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(if (fa) "منابع اشتراک" else "Subscriptions", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(if (fa) "منبع رسمی و منابع سفارشی" else "Official and custom sources", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
                    }
                    OutlinedButton(
                        onClick = {
                            if (!updating) scope.launch {
                                updating = true
                                try {
                                    repo.refreshAll().fold(
                                        onSuccess = { count -> updateSucceeded = true; updateMessage = if (fa) "بروزرسانی موفق بود؛ $count پروکسی در فهرست موجود است." else "Update succeeded; $count proxies are available." },
                                        onFailure = { error -> updateSucceeded = false; updateMessage = if (fa) "بروزرسانی ناموفق: ${error.message ?: "خطای ناشناخته"}" else "Update failed: ${error.message ?: "Unknown error"}" }
                                    )
                                } finally { updating = false }
                            }
                        }, enabled = !updating, shape = RoundedCornerShape(13.dp)
                    ) {
                        Icon(if (updating) Icons.Default.HourglassTop else Icons.Default.Sync, null)
                        Spacer(Modifier.width(5.dp))
                        Text(if (updating) (if (fa) "در حال بروزرسانی" else "Updating") else "Update Subscription")
                    }
                }
            }
            updateMessage?.let { message ->
                Text(message, color = if (updateSucceeded) Color(0xFF168A58) else Color(0xFFD13C3C), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(15.dp), label = { Text(if (fa) "آدرس فایل TXT" else "TXT subscription URL") })
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (!updating) scope.launch {
                        val candidate = url.trim()
                        updating = true
                        updateMessage = null
                        try {
                            if (candidate.isBlank()) {
                                updateSucceeded = false
                                updateMessage = if (fa) "لینک شما معتبر نیست" else "Your link is invalid"
                            } else {
                                repo.addSource(candidate).fold(
                                    onSuccess = {
                                        url = ""
                                        runCatching { repo.refreshAll() }
                                        val source = repo.sourceByUrl(candidate)
                                        if (source?.online == true && source.proxyCount > 0) {
                                            updateSucceeded = true
                                            updateMessage = if (fa) "سابسکرایپشن پروکسی با موفقیت به مخزن اضافه شد؛ ${source.proxyCount} پروکسی دریافت شد." else "Proxy subscription added successfully; ${source.proxyCount} proxies received."
                                        } else {
                                            updateSucceeded = false
                                            val reason = source?.lastError ?: if (fa) "دریافت پروکسی ناموفق بود" else "Proxy retrieval failed"
                                            updateMessage = if (fa) "منبع اضافه شد اما دریافت ناموفق بود: $reason" else "Source added, but retrieval failed: $reason"
                                        }
                                    },
                                    onFailure = { error ->
                                        updateSucceeded = false
                                        updateMessage = if (fa) "لینک شما معتبر نیست: ${error.message ?: "آدرس باید با .txt تمام شود"}" else "Your link is invalid: ${error.message ?: "The URL must end with .txt"}"
                                    }
                                )
                            }
                        } finally {
                            updating = false
                        }
                    }
                },
                enabled = !updating,
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(if (updating) Icons.Default.HourglassTop else Icons.Default.Add, null)
                Spacer(Modifier.width(5.dp))
                Text(if (updating) (if (fa) "در حال افزودن" else "Adding") else (if (fa) "افزودن منبع" else "Add source"))
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sources, key = { it.id }) { source ->
                    Card(shape = RoundedCornerShape(17.dp)) {
                        ListItem(
                            headlineContent = { Text(if (source.official) "Official Source" else source.url, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            supportingContent = { Text(if (source.online == true) "Online · ${source.proxyCount} proxies" else source.lastError ?: "Waiting for automatic update", maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                            trailingContent = { if (!source.official) IconButton({ scope.launch { repo.deleteSource(source.id) } }) { Icon(Icons.Default.DeleteOutline, if (fa) "حذف" else "Delete") } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(settings: Settings, store: SettingsStore, fa: Boolean, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val compact = maxWidth < 520.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text(if (fa) "تنظیمات" else "Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        SettingGroup(if (fa) "عمومی" else "GENERAL") {
            Text(if (fa) "زبان" else "Language", fontWeight = FontWeight.SemiBold)
            Row {
                FilterChip(settings.language == AppLanguage.PERSIAN, { scope.launch { store.language(AppLanguage.PERSIAN) } }, label = { Text("فارسی") })
                Spacer(Modifier.width(8.dp))
                FilterChip(settings.language == AppLanguage.ENGLISH, { scope.launch { store.language(AppLanguage.ENGLISH) } }, label = { Text("English") })
            }
            Spacer(Modifier.height(14.dp))
            Text(if (fa) "تم ظاهری" else "Appearance", fontWeight = FontWeight.SemiBold)
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppTheme.values().forEach { theme -> FilterChip(settings.theme == theme, { scope.launch { store.theme(theme) } }, modifier = Modifier.fillMaxWidth(), label = { Text(theme.name) }) }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AppTheme.values().forEach { theme -> FilterChip(settings.theme == theme, { scope.launch { store.theme(theme) } }, label = { Text(theme.name) }) }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(if (fa) "اندازه نوشته‌ها" else "Text size", fontWeight = FontWeight.SemiBold)
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AppTextSize.values().forEach { size ->
                        FilterChip(settings.textSize == size, { scope.launch { store.textSize(size) } }, modifier = Modifier.fillMaxWidth(), label = { Text(textSizeLabel(size, fa)) })
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    AppTextSize.values().forEach { size ->
                        FilterChip(settings.textSize == size, { scope.launch { store.textSize(size) } }, label = { Text(textSizeLabel(size, fa)) })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SettingGroup(if (fa) "بروزرسانی خودکار" else "AUTO UPDATE") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (fa) "بروزرسانی خودکار" else "Automatic updates", fontWeight = FontWeight.SemiBold)
                    Text(if (settings.autoUpdateEnabled) (if (fa) "فعال" else "Enabled") else (if (fa) "خاموش" else "Off"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
                }
                Switch(settings.autoUpdateEnabled, { scope.launch { store.autoUpdateEnabled(it) } })
            }
            Spacer(Modifier.height(12.dp))
            Text(if (fa) "بازه زمانی" else "Update interval", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (settings.autoUpdateEnabled) 1f else .4f))
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(15L, 30L, 45L, 60L).forEach { minutes ->
                        FilterChip(settings.intervalMinutes == minutes, { scope.launch { store.interval(minutes) } }, modifier = Modifier.fillMaxWidth(), enabled = settings.autoUpdateEnabled, label = { Text(if (minutes == 60L) "1 ${if (fa) "ساعت" else "hour"}" else "$minutes ${if (fa) "دقیقه" else "min"}") })
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(15L, 30L, 45L, 60L).forEach { minutes ->
                        FilterChip(settings.intervalMinutes == minutes, { scope.launch { store.interval(minutes) } }, enabled = settings.autoUpdateEnabled, label = { Text(if (minutes == 60L) "1 ${if (fa) "ساعت" else "hour"}" else "$minutes ${if (fa) "دقیقه" else "min"}") })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (fa) "فقط با Wi‑Fi" else "Wi‑Fi only", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (settings.autoUpdateEnabled) 1f else .4f))
                Switch(settings.wifiOnly, { scope.launch { store.wifiOnly(it) } }, enabled = settings.autoUpdateEnabled)
            }
            Text(if (fa) "با خاموش‌کردن، فقط بروزرسانی دستی فعال می‌ماند." else "When off, only manual updates remain active.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        SettingGroup(if (fa) "حذف خودکار پروکسی‌های قدیمی" else "AUTO CLEANUP") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (fa) "حذف خودکار" else "Automatic cleanup", fontWeight = FontWeight.SemiBold)
                    Text(if (settings.autoDeleteEnabled) (if (fa) "فعال" else "Enabled") else (if (fa) "خاموش" else "Off"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
                }
                Switch(settings.autoDeleteEnabled, { scope.launch { store.autoDeleteEnabled(it) } })
            }
            Spacer(Modifier.height(12.dp))
            Text(if (fa) "پروکسی‌هایی که در این مدت دیده نشده‌اند حذف می‌شوند" else "Proxies not seen during this period will be removed", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(4L, 8L, 12L, 24L).forEach { hours ->
                        FilterChip(settings.autoDeleteHours == hours, { scope.launch { store.autoDeleteHours(hours) } }, modifier = Modifier.fillMaxWidth(), enabled = settings.autoDeleteEnabled, label = { Text("${hours}H") })
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(4L, 8L, 12L, 24L).forEach { hours ->
                        FilterChip(settings.autoDeleteHours == hours, { scope.launch { store.autoDeleteHours(hours) } }, enabled = settings.autoDeleteEnabled, label = { Text("${hours}H") })
                    }
                }
            }
            Text(if (fa) "زمان اجرا مطابق ساعت گوشی و حتی خارج از برنامه انجام می‌شود." else "Runs on the phone clock even when the app is closed.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), fontSize = 12.sp)
        }
        Spacer(Modifier.height(16.dp))
        AboutGroup(fa)
        Spacer(Modifier.height(16.dp))
        PrivacyGroup(fa)
        }
    }
}

@Composable private fun AboutGroup(fa: Boolean) {
    val context = LocalContext.current
    SettingGroup(if (fa) "درباره" else "ABOUT") {
        Text("Radar Proxy", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text("Native Android · v1.6.4", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
        Spacer(Modifier.height(10.dp))
        AboutLink("Telegram", "https://t.me/Radar_Proxy", Icons.Default.Send, context)
        AboutLink("Github", "https://github.com/KhodeJav/Radar-Proxy", Icons.Default.Code, context)
        Spacer(Modifier.height(6.dp))
        AboutLink("Donate With Crypto", "https://Payments.tipora.ir", Icons.Default.FavoriteBorder, context)
    }
}

@Composable private fun PrivacyGroup(fa: Boolean) {
    val context = LocalContext.current
    SettingGroup(if (fa) "حریم خصوصی" else "PRIVACY") {
        AboutLink(if (fa) "پروژه متن‌باز" else "Open-source project", "https://github.com/KhodeJav/Radar-Proxy", Icons.Default.Code, context)
        Text(if (fa) "این برنامه توسط @Radar_Proxy ساخته شده است؛ کپی‌برداری ممنوع است." else "This app was created by @Radar_Proxy; copying is prohibited.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 12.sp)
        Text(if (fa) "بدون API، دیتابیس و دسترسی اضافی." else "No API, database, or extra permissions.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 12.sp)
    }
}

@Composable private fun AboutLink(label: String, url: String, icon: ImageVector, context: Context) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(url.removePrefix("https://"), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 12.sp)
        }
    }
}

@Composable private fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) { Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp)).background(MaterialTheme.colorScheme.surface).padding(17.dp)) { Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); content() } }
private fun textSizeLabel(value: AppTextSize, fa: Boolean) = when (value) { AppTextSize.LARGE -> if (fa) "بزرگ" else "Large"; AppTextSize.SMALL -> if (fa) "کوچک" else "Small"; AppTextSize.VERY_SMALL -> if (fa) "خیلی کوچک" else "Very small" }
private fun sortLabel(mode: SortMode, fa: Boolean) = when (mode) { SortMode.DEFAULT -> if (fa) "پیش‌فرض" else "Default"; SortMode.FASTEST -> if (fa) "کمترین پینگ" else "Fastest ping"; SortMode.SLOWEST -> if (fa) "بیشترین پینگ" else "Slowest ping"; SortMode.NEWEST -> if (fa) "جدیدترین" else "Newest"; SortMode.OLDEST -> if (fa) "قدیمی‌ترین" else "Oldest" }
