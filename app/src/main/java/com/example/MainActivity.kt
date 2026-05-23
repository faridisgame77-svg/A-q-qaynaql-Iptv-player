package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.IptvConfig
import com.example.data.IptvItem
import com.example.ui.IptvViewModel
import com.example.ui.components.VideoPlayer
import com.example.ui.theme.BorderGray
import com.example.ui.theme.DeepSpace
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonBlue
import com.example.ui.theme.SpaceCard
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextWhite

class MainActivity : ComponentActivity() {
    private val viewModel: IptvViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = DeepSpace
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        AppNavigation(viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: IptvViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val activeConfig by viewModel.activeConfig.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusText by viewModel.loadingStatusText.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentScreen) {
            "LOGIN" -> {
                LoginScreen(viewModel)
            }
            "LOADING" -> {
                LoadingIndicatorScreen(statusText)
            }
            "DASHBOARD" -> {
                DashboardScreen(viewModel)
            }
            "SERIES_DETAILS" -> {
                SeriesDetailsScreen(viewModel)
            }
            "PLAYER" -> {
                val url by viewModel.activeStreamUrl.collectAsState()
                val title by viewModel.activeStreamTitle.collectAsState()
                VideoPlayer(
                    videoUrl = url,
                    title = title,
                    onBack = { viewModel.stopPlayer() }
                )
            }
        }

        // Global Overlay for async loads on top of active screens
        if (isLoading && currentScreen != "LOADING") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = NeonBlue)
            }
        }
    }
}

// Custom extension Modifier to add highly visible TV focus rings and hover scales
@Composable
fun Modifier.tvFocusable(
    focusBorderColor: Color = MaterialTheme.colorScheme.secondary,
    focusScale: Float = 1.05f,
    onFocusChanged: (Boolean) -> Unit = {}
): Modifier {
    var isFocused by remember { mutableStateOf(false) }
    return this
        .onFocusChanged {
            isFocused = it.isFocused
            onFocusChanged(it.isFocused)
        }
        .border(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) focusBorderColor else BorderGray.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp)
        )
        .scale(if (isFocused) focusScale else 1.0f)
        .focusable()
}

@Composable
fun LoadingIndicatorScreen(statusText: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = NeonBlue,
                strokeWidth = 4.dp,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = statusText,
                color = TextWhite,
                fontSize = 18.sp,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun LoginScreen(viewModel: IptvViewModel) {
    val savedConfigs by viewModel.savedConfigs.collectAsState()
    var selectedLoginMethod by remember { mutableStateOf("QR") } // "QR" | "MANUAL"
    
    // Manual setup states
    var xtreamServer by remember { mutableStateOf("") }
    var xtreamUser by remember { mutableStateOf("") }
    var xtreamPass by remember { mutableStateOf("") }
    var m3uLink by remember { mutableStateOf("") }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = list(DeepSpace, Color(0xFF0C0F17))
                )
            )
            .padding(24.dp)
    ) {
        // Left Column: Portals & Inputs configuration
        Column(
            modifier = Modifier
                .weight(1.2f)
                .fillMaxHeight()
                .padding(end = 16.dp)
        ) {
            Text(
                text = "X3M IPTV PLAYER",
                style = MaterialTheme.typography.headlineMedium,
                color = NeonBlue,
                fontSize = 28.sp
            )
            Text(
                text = "TV-ni idarə etmək üçün aşağıdakı giriş üsulunu seçin",
                color = TextMuted,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Portal Tab Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // QR Tab
                TabButton(
                    text = "QR/Web Mobil Giriş",
                    isSelected = selectedLoginMethod == "QR",
                    onClick = { selectedLoginMethod = "QR" }
                )
                // Manual Tab
                TabButton(
                    text = "Manual Giriş (Xtream/M3U)",
                    isSelected = selectedLoginMethod == "MANUAL",
                    onClick = { selectedLoginMethod = "MANUAL" }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dynamic Tab Views
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (selectedLoginMethod == "QR") {
                    QRLoginSection(viewModel.deploymentServerUrl)
                } else {
                    ManualLoginSection(
                        xtreamServer, { xtreamServer = it },
                        xtreamUser, { xtreamUser = it },
                        xtreamPass, { xtreamPass = it },
                        m3uLink, { m3uLink = it },
                        onSubmitXtream = {
                            if (xtreamServer.isNotEmpty() && xtreamUser.isNotEmpty() && xtreamPass.isNotEmpty()) {
                                viewModel.addManualCredentials("XTREAM", xtreamServer, xtreamUser, xtreamPass)
                            }
                        },
                        onSubmitM3u = {
                            if (m3uLink.isNotEmpty()) {
                                viewModel.addManualCredentials("M3U", m3uLink)
                            }
                        }
                    )
                }
            }
        }

        // Divider
        VerticalDivider(
            color = BorderGray,
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp)
        )

        // Right Column: Saved Profiles ("Şifrə prefereansı & ömürlük qalma")
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = "Yadda Saxlanılan Hesablar",
                style = MaterialTheme.typography.titleMedium,
                color = TextWhite,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (savedConfigs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, BorderGray, RoundedCornerShape(8.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hələ ki, heç bir profil yoxdur.",
                            color = TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(savedConfigs) { config ->
                        SavedAccountCard(config, onSelect = {
                            viewModel.selectSavedConfig(config)
                        }, onDelete = {
                            viewModel.deleteSavedConfig(config)
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .onFocusChanged { isFocused = it.isFocused }
            .border(
                width = if (isSelected || isFocused) 2.dp else 1.dp,
                color = if (isSelected) NeonBlue else if (isFocused) MaterialTheme.colorScheme.secondary else BorderGray,
                shape = RoundedCornerShape(8.dp)
            )
            .background(
                if (isSelected) NeonBlue.copy(alpha = 0.15f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .focusable()
    ) {
        Text(
            text = text,
            color = if (isSelected) NeonBlue else TextMuted,
            fontSize = 14.sp,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

@Composable
fun QRLoginSection(deploymentUrl: String) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = SpaceCard),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simulated QR code drawing onto Custom canvas
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                SimulatedQrCode(modifier = Modifier.fillMaxSize())
            }

            Spacer(modifier = Modifier.width(20.dp))

            Column {
                Text(
                    text = "Lokal Serverlə Giriş",
                    color = NeonBlue,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Noutbuk və ya telefonunuzla televizorla eyni Wi-Fi şəbəkəsinə qoşulun. Aşağıdakı ünvana daxil olun yaxud QR kodu oxudun:",
                    color = TextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                // Beautiful neon board URL indicator
                Box(
                    modifier = Modifier
                        .background(Color.Black, RoundedCornerShape(6.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = deploymentUrl,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 16.sp,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Açılan web səhifədə M3U faylını və ya Xtream məlumatlarını qeyd edib 'Deploy' klikləyin.",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun ManualLoginSection(
    server: String, onServerChange: (String) -> Unit,
    user: String, onUserChange: (String) -> Unit,
    pass: String, onPassChange: (String) -> Unit,
    m3u: String, onM3uChange: (String) -> Unit,
    onSubmitXtream: () -> Unit,
    onSubmitM3u: () -> Unit
) {
    var manualMode by remember { mutableStateOf("XTREAM") } // "XTREAM" | "M3U"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(SpaceCard)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { manualMode = "XTREAM" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manualMode == "XTREAM") NeonBlue else Color.Transparent,
                    contentColor = if (manualMode == "XTREAM") Color.Black else TextWhite
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Xtream Codes")
            }
            Button(
                onClick = { manualMode = "M3U" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (manualMode == "M3U") NeonBlue else Color.Transparent,
                    contentColor = if (manualMode == "M3U") Color.Black else TextWhite
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("M3U Linkilə")
            }
        }

        if (manualMode == "XTREAM") {
            // Xtream Form
            OutlinedTextField(
                value = server,
                onValueChange = onServerChange,
                label = { Text("Server URL (və ya IP:Port)", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = BorderGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = user,
                    onValueChange = onUserChange,
                    label = { Text("İstifadəçi adı", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = BorderGray
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = onPassChange,
                    label = { Text("Şifrə", color = TextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        focusedBorderColor = NeonBlue,
                        unfocusedBorderColor = BorderGray
                    ),
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onSubmitXtream,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(MaterialTheme.colorScheme.secondary, 1.02f),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color.Black)
            ) {
                Text("Daxil Ol", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            // M3U Form
            OutlinedTextField(
                value = m3u,
                onValueChange = onM3uChange,
                label = { Text("M3U Playlist URL Linki", color = TextMuted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextWhite,
                    unfocusedTextColor = TextWhite,
                    focusedBorderColor = NeonBlue,
                    unfocusedBorderColor = BorderGray
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                singleLine = true
            )
            Button(
                onClick = onSubmitM3u,
                modifier = Modifier
                    .fillMaxWidth()
                    .tvFocusable(MaterialTheme.colorScheme.secondary, 1.02f),
                colors = ButtonDefaults.buttonColors(containerColor = NeonBlue, contentColor = Color.Black)
            ) {
                Text("Playlisti Yüklə", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun SavedAccountCard(
    config: IptvConfig,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(MaterialTheme.colorScheme.secondary, 1.03f)
            .clickable(onClick = onSelect),
        colors = CardDefaults.cardColors(containerColor = SpaceCard),
        border = BorderStroke(1.dp, BorderGray)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (config.type == "XTREAM") Icons.Default.Tv else Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = NeonBlue,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = config.name,
                        color = TextWhite,
                        fontSize = 15.sp,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = if (config.type == "XTREAM") "Xtream Codes API Portal" else "M3U Playlist",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Sil",
                    tint = Color.Red.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// Simulated QR code block drawing
@Composable
fun SimulatedQrCode(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val sizePx = size.width
        val cells = 21
        val cellSize = sizePx / cells
        
        // Finder: Top-Left
        drawRect(Color.Black, size = androidx.compose.ui.geometry.Size(cellSize * 7, cellSize * 7))
        drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(cellSize, cellSize), size = androidx.compose.ui.geometry.Size(cellSize * 5, cellSize * 5))
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(cellSize * 2, cellSize * 2), size = androidx.compose.ui.geometry.Size(cellSize * 3, cellSize * 3))
        
        // Finder: Top-Right
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(cellSize * 14, 0f), size = androidx.compose.ui.geometry.Size(cellSize * 7, cellSize * 7))
        drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(cellSize * 15, cellSize), size = androidx.compose.ui.geometry.Size(cellSize * 5, cellSize * 5))
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(cellSize * 16, cellSize * 2), size = androidx.compose.ui.geometry.Size(cellSize * 3, cellSize * 3))
        
        // Finder: Bottom-Left
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(0f, cellSize * 14), size = androidx.compose.ui.geometry.Size(cellSize * 7, cellSize * 7))
        drawRect(Color.White, topLeft = androidx.compose.ui.geometry.Offset(cellSize, cellSize * 15), size = androidx.compose.ui.geometry.Size(cellSize * 5, cellSize * 5))
        drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(cellSize * 2, cellSize * 16), size = androidx.compose.ui.geometry.Size(cellSize * 3, cellSize * 3))

        // Timing patterns
        for (i in 8..12) {
            if (i % 2 == 0) {
                drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(cellSize * i, cellSize * 6), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
                drawRect(Color.Black, topLeft = androidx.compose.ui.geometry.Offset(cellSize * 6, cellSize * i), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
            }
        }

        // Pseudo-random data pixels
        val random = java.util.Random(10452)
        for (r in 0 until cells) {
            for (c in 0 until cells) {
                if (r < 8 && c < 8) continue
                if (r < 8 && c > 12) continue
                if (r > 12 && c < 8) continue
                if (r == 6 || c == 6) continue

                if (random.nextBoolean()) {
                    drawRect(
                        Color.Black,
                        topLeft = androidx.compose.ui.geometry.Offset(c * cellSize, r * cellSize),
                        size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: IptvViewModel) {
    val activeConfig by viewModel.activeConfig.collectAsState()
    val tab by viewModel.currentDashboardTab.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val filteredItems by viewModel.filteredItems.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
    ) {
        // COLUMN 1: Dashboard Sidebar Controls
        Column(
            modifier = Modifier
                .width(200.dp)
                .fillMaxHeight()
                .background(Color(0xFF0A0D15))
                .padding(vertical = 16.dp, horizontal = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // TV Logo Accent
                Text(
                    text = "X3M PLAYER",
                    color = NeonBlue,
                    fontSize = 20.sp,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Sidebar items representing target directories
                SidebarItem(
                    text = "Canlı TV",
                    icon = Icons.Default.Tv,
                    isSelected = tab == "LIVE",
                    onClick = { viewModel.changeDashboardTab("LIVE") }
                )
                SidebarItem(
                    text = "Filmlər",
                    icon = Icons.Default.Movie,
                    isSelected = tab == "MOVIE",
                    onClick = { viewModel.changeDashboardTab("MOVIE") }
                )
                SidebarItem(
                    text = "Seriallar",
                    icon = Icons.Default.VideoLibrary,
                    isSelected = tab == "SERIES",
                    onClick = { viewModel.changeDashboardTab("SERIES") }
                )
            }

            // Bottom action: Logout Account ("istəsək, xüsusi məlumatdan çıxıb M3U faylına keçmək üçün çıxış etmək")
            SidebarItem(
                text = "Çıxış Et",
                icon = Icons.Default.ExitToApp,
                isSelected = false,
                onClick = { viewModel.logOutCurrentAccount() },
                color = Color.Red.copy(alpha = 0.8f)
            )
        }

        // COLUMN 2: Categories sidebar list
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0xFF0F121C))
                .padding(vertical = 12.dp)
        ) {
            Text(
                text = "Kateqoriyalar",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
            )

            if (categories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Boşdur", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxHeight()) {
                    items(categories) { catName ->
                        CategoryListItem(
                            name = catName,
                            isSelected = selectedCategory == catName,
                            onClick = { viewModel.selectCategory(catName) }
                        )
                    }
                }
            }
        }

        // COLUMN 3: Channel Content Grid View
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
        ) {
            // Heading details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedCategory ?: "Bütün Kanallar",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextWhite,
                    fontSize = 18.sp
                )
                Text(
                    text = "${filteredItems.size} İtem tapıldı",
                    color = TextMuted,
                    fontSize = 12.sp
                )
            }

            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Tv,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Bu kateqoriyada video asset tapılmadı", color = TextMuted, fontSize = 14.sp)
                    }
                }
            } else {
                val config = LocalConfiguration.current
                val isTvLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
                val columns = if (isTvLandscape) 5 else 2

                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredItems) { item ->
                        ContentGridCard(
                            item = item,
                            onClick = {
                                if (item.type == "SERIES") {
                                    viewModel.selectSeries(item)
                                } else {
                                    // Play live stream or movie
                                    viewModel.launchPlayer(item.url, item.title)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    color: Color = NeonBlue
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) color.copy(alpha = 0.15f)
                else if (isFocused) Color.White.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) color else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .focusable()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = if (isSelected || isFocused) color else TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = text,
                color = if (isSelected || isFocused) TextWhite else TextMuted,
                fontSize = 14.sp,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun CategoryListItem(
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .background(
                if (isSelected) NeonBlue.copy(alpha = 0.12f)
                else if (isFocused) Color.White.copy(alpha = 0.05f)
                else Color.Transparent
            )
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) NeonBlue else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .focusable()
    ) {
        Text(
            text = name,
            color = if (isSelected || isFocused) TextWhite else TextMuted,
            fontSize = 13.sp,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ContentGridCard(
    item: IptvItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(MaterialTheme.colorScheme.secondary, 1.05f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SpaceCard),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.4f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                if (item.logoUrl != null) {
                    AsyncImage(
                        model = item.logoUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    // Styled Fallback Poster
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (item.type == "SERIES") Icons.Default.VideoLibrary else Icons.Default.Tv,
                            contentDescription = null,
                            tint = NeonBlue,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }

            // Title block
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text(
                    text = item.title,
                    color = TextWhite,
                    fontSize = 12.sp,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

// Netflix Kartı-like Series Season details screen ("Seriallarda sezon mövsüm seçimi də olacaq")
@Composable
fun SeriesDetailsScreen(viewModel: IptvViewModel) {
    val series by viewModel.selectedSeries.collectAsState()
    val episodes by viewModel.seriesEpisodes.collectAsState()
    
    // Group episodes by season dynamically mapping to seasons keys
    val epGrouped = remember(episodes) {
        episodes.groupBy { it.seasonNumber ?: 1 }
    }
    val seasons = remember(epGrouped) {
        epGrouped.keys.sorted()
    }
    var selectedSeason by remember { mutableStateOf<Int?>(null) }
    
    // Auto preset first season when loaded
    LaunchedEffect(seasons) {
        if (seasons.isNotEmpty() && selectedSeason == null) {
            selectedSeason = seasons.first()
        }
    }

    val activeSeasonNum = selectedSeason ?: 1
    val currentSeasonEpisodes = epGrouped[activeSeasonNum] ?: emptyList()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepSpace)
            .padding(24.dp)
    ) {
        // LEFT COLUMN: Netflix Series Poster Details
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            // BACK Tab bar
            Box(
                modifier = Modifier
                    .tvFocusable(MaterialTheme.colorScheme.secondary, 1.05f)
                    .clickable { viewModel.goBackToDashboard() }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Dala", tint = NeonBlue)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Geri dön", color = TextWhite, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Series Poster
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, BorderGray)
                    .background(SpaceCard),
                contentAlignment = Alignment.Center
            ) {
                series?.let { s ->
                    if (s.logoUrl != null) {
                        AsyncImage(
                            model = s.logoUrl,
                            contentDescription = s.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(imageVector = Icons.Default.VideoLibrary, contentDescription = null, tint = NeonBlue, modifier = Modifier.size(56.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = series?.title ?: "Unknown Series",
                color = TextWhite,
                fontSize = 22.sp,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = series?.category ?: "Seriallar",
                color = TextMuted,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Serialın seon və bölümlərini seçərək izləyə bilərsiniz. Möhtəşəm səs və axıcı görüntü ilə ev kinoteatrı keyfi.",
                color = TextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }

        // SPACER
        Spacer(modifier = Modifier.width(24.dp))

        // RIGHT COLUMN: Netflix Series Seasons and Episodes Selection
        Column(
            modifier = Modifier
                .weight(1.8f)
                .fillMaxHeight()
        ) {
            // Seasons list
            Text(
                text = "Mövsüm/Sezon Seçimi",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(bottom = 18.dp)
            ) {
                if (seasons.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(SpaceCard, RoundedCornerShape(6.dp))
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("Yüklənir...", color = TextMuted, fontSize = 14.sp)
                    }
                } else {
                    seasons.forEach { seasonNum ->
                        val isActive = selectedSeason == seasonNum
                        var isFocused by remember { mutableStateOf(false) }
                        
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .border(
                                    width = if (isActive || isFocused) 2.dp else 1.dp,
                                    color = if (isActive) NeonBlue else if (isFocused) MaterialTheme.colorScheme.secondary else BorderGray,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (isActive) NeonBlue.copy(alpha = 0.2f) else SpaceCard,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { selectedSeason = seasonNum }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .focusable()
                        ) {
                            Text(
                                text = "Sezon $seasonNum",
                                color = if (isActive) NeonBlue else TextWhite,
                                fontSize = 14.sp,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                    }
                }
            }

            // Episodes list scroll
            Text(
                text = "Bölümlər",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (currentSeasonEpisodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Bu mövsümdə bölüm tapılmadı.", color = TextMuted, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(currentSeasonEpisodes) { episode ->
                        EpisodeRow(
                            episode = episode,
                            onClick = {
                                viewModel.launchPlayer(episode.url, episode.title)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeRow(
    episode: IptvItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(MaterialTheme.colorScheme.secondary, 1.02f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SpaceCard),
        border = BorderStroke(1.dp, BorderGray.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = NeonBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = "Bölüm ${episode.episodeNumber ?: 1}: ${episode.title}",
                    color = TextWhite,
                    fontSize = 14.sp,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "Görüntü kalitesi: UltraHD .axın can",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// Single list macro declaration helper
fun <T> list(vararg elements: T): List<T> = elements.toList()
