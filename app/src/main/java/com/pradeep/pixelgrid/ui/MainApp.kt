package com.pradeep.pixelgrid.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pradeep.pixelgrid.data.MediaItem
import com.pradeep.pixelgrid.data.MediaRepository
import com.pradeep.pixelgrid.data.UpdateInfo
import com.pradeep.pixelgrid.data.UpdateManager
import com.pradeep.pixelgrid.ui.components.ShadcnButton
import com.pradeep.pixelgrid.ui.components.ShadcnButtonVariant
import com.pradeep.pixelgrid.ui.components.ShadcnDialog
import com.pradeep.pixelgrid.ui.components.ShadcnTopBar
import com.pradeep.pixelgrid.ui.screens.*
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pixelvault_settings"
private const val KEY_COLUMNS = "grid_columns"
private const val KEY_AUTOPLAY = "video_autoplay"

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainApp(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Storage & UI States
    var mediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var hasPermission by remember { mutableStateOf(hasRequiredPermissions(context)) }

    // Persistent columns preference
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var gridColumns by remember { mutableStateOf(prefs.getInt(KEY_COLUMNS, 3)) }
    var videoAutoplay by remember { mutableStateOf(prefs.getBoolean(KEY_AUTOPLAY, true)) }

    // Navigation and screen focus states
    var currentTab by remember { mutableStateOf(0) }
    
    // Swipe pager viewer states
    var viewerMediaList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var viewerInitialIndex by remember { mutableStateOf(-1) }
    var itemToDelete by remember { mutableStateOf<MediaItem?>(null) }

    // Update checker states
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var downloadProgress by remember { mutableStateOf<Float?>(null) }
    var downloadedFile by remember { mutableStateOf<java.io.File?>(null) }

    // Refresh function to reload MediaStore content
    val refreshMedia: () -> Unit = {
        if (hasPermission) {
            isLoading = true
            coroutineScope.launch {
                mediaList = MediaRepository.fetchMediaList(context)
                isLoading = false
            }
        }
    }

    // Permission launcher request
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_IMAGES] == true &&
            permissions[Manifest.permission.READ_MEDIA_VIDEO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        hasPermission = granted
        if (granted) {
            refreshMedia()
        }
    }

    // Recoverable security exception launcher for viewer deletion dialog
    val viewerDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val deleted = itemToDelete
            if (deleted != null) {
                viewerMediaList = viewerMediaList.filter { it.id != deleted.id }
                refreshMedia()
                if (viewerMediaList.isEmpty()) {
                    viewerInitialIndex = -1
                }
            }
            itemToDelete = null
        }
    }

    // Handle single item deletion in viewer
    val deleteViewerItem: (MediaItem) -> Unit = { item ->
        itemToDelete = item
        coroutineScope.launch {
            try {
                val sender = MediaRepository.deleteMediaItem(context, item)
                if (sender != null) {
                    viewerDeleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                } else {
                    viewerMediaList = viewerMediaList.filter { it.id != item.id }
                    refreshMedia()
                    if (viewerMediaList.isEmpty()) {
                        viewerInitialIndex = -1
                    }
                    itemToDelete = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                itemToDelete = null
            }
        }
    }

    // Manual check updates trigger
    val triggerManualUpdateCheck: () -> Unit = {
        isLoading = true
        coroutineScope.launch {
            val info = UpdateManager.checkForUpdates(context, isManualCheck = true)
            isLoading = false
            if (info != null) {
                updateInfo = info
            } else {
                Toast.makeText(context, "Your app is up to date!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Initial load
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            refreshMedia()
            
            // Check for updates on startup
            coroutineScope.launch {
                val info = UpdateManager.checkForUpdates(context, isManualCheck = false)
                if (info != null) {
                    updateInfo = info
                }
            }
        }
    }

    // --- MAIN UI LAYOUT ---
    if (!hasPermission) {
        // ONBOARDING SCREEN
        PermissionRequiredScreen {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissions)
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    if (viewerInitialIndex == -1) {
                        ShadcnTopBar(
                            title = "PixelVault",
                            navigationIcon = {
                                Icon(
                                    painter = painterResource(id = com.pradeep.pixelgrid.R.drawable.ic_launcher_foreground),
                                    contentDescription = "PixelVault Logo",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        )
                    }
                },
                bottomBar = {
                    // Custom navigation bar styled like Shadcn Tablist
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp,
                            modifier = Modifier.navigationBarsPadding().height(64.dp)
                        ) {
                            val tabs = listOf(
                                NavigationTab("Photos", Icons.Default.Home),
                                NavigationTab("Albums", Icons.Default.List),
                                NavigationTab("Favorites", Icons.Default.Favorite),
                                NavigationTab("Settings", Icons.Default.Settings)
                            )
                            
                            tabs.forEachIndexed { index, tab ->
                                val isSelected = currentTab == index
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { currentTab = index },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.label,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = MaterialTheme.colorScheme.secondary
                                    )
                                )
                            }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            top = paddingValues.calculateTopPadding(),
                            bottom = paddingValues.calculateBottomPadding()
                        )
                ) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        when (currentTab) {
                            0 -> PhotosScreen(
                                mediaList = mediaList,
                                gridColumns = gridColumns,
                                onMediaClick = { list, index ->
                                    viewerMediaList = list
                                    viewerInitialIndex = index
                                },
                                onRefresh = refreshMedia
                            )
                            1 -> AlbumsScreen(
                                mediaList = mediaList,
                                gridColumns = gridColumns,
                                onMediaClick = { list, index ->
                                    viewerMediaList = list
                                    viewerInitialIndex = index
                                }
                            )
                            2 -> FavoritesScreen(
                                mediaList = mediaList,
                                gridColumns = gridColumns,
                                onMediaClick = { list, index ->
                                    viewerMediaList = list
                                    viewerInitialIndex = index
                                }
                            )
                            3 -> {
                                val totalSize = remember(mediaList) { mediaList.sumOf { it.size } }
                                SettingsScreen(
                                    darkTheme = darkTheme,
                                    onThemeChange = onThemeChange,
                                    gridColumns = gridColumns,
                                    onColumnsChange = { cols ->
                                        gridColumns = cols
                                        prefs.edit().putInt(KEY_COLUMNS, cols).apply()
                                    },
                                    videoAutoplay = videoAutoplay,
                                    onVideoAutoplayChange = { autoplay ->
                                        videoAutoplay = autoplay
                                        prefs.edit().putBoolean(KEY_AUTOPLAY, autoplay).apply()
                                    },
                                    totalCount = mediaList.size,
                                    totalSize = totalSize,
                                    onCheckForUpdates = triggerManualUpdateCheck
                                )
                            }
                        }
                    }
                }
            }

            // --- FULL SCREEN OVERLAY VIEW SCREEN ---
            AnimatedVisibility(
                visible = viewerInitialIndex != -1,
                enter = fadeIn(animationSpec = tween(250)) + slideInVertically(initialOffsetY = { it / 3 }, animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { it / 3 }, animationSpec = tween(200))
            ) {
                if (viewerInitialIndex != -1) {
                    val favoriteIds = remember(mediaList) { MediaRepository.getFavoriteIds(context) }
                    // Map items to update favorite states dynamically on paging
                    val listWithFavorites = viewerMediaList.map { it.copy(isFavorite = favoriteIds.contains(it.id)) }

                    ViewerScreen(
                        mediaList = listWithFavorites,
                        initialIndex = viewerInitialIndex,
                        onBack = { viewerInitialIndex = -1 },
                        videoAutoplay = videoAutoplay,
                        darkTheme = darkTheme,
                        onFavoriteToggle = { clickedItem ->
                            MediaRepository.toggleFavorite(context, clickedItem.id)
                            refreshMedia()
                        },
                        onDeleteMedia = deleteViewerItem
                    )
                }
            }

            // --- SHADCN UPDATE CHECKER DIALOG ---
            updateInfo?.let { info ->
                ShadcnDialog(
                    onDismissRequest = {
                        if (downloadProgress == null) {
                            updateInfo = null
                            downloadedFile = null
                        }
                    },
                    title = "Update Available",
                    description = "A new version of PixelVault is ready to download."
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    ) {
                        // App Logo
                        Icon(
                            painter = painterResource(id = com.pradeep.pixelgrid.R.drawable.ic_launcher_foreground),
                            contentDescription = "PixelVault Logo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(64.dp)
                        )

                        // App Name and Version
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "PixelVault",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "New Version: ${info.version}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Changelog scroll box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 120.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = info.changelog,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                            )
                        }

                        // Progress Bar or install status
                        if (downloadProgress != null) {
                            val progress = downloadProgress!!
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Downloading APK: ${(progress * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                com.pradeep.pixelgrid.ui.components.ShadcnProgressBar(
                                    progress = progress,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else if (downloadedFile != null) {
                            Text(
                                text = "Download complete. Ready to install.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = com.pradeep.pixelgrid.ui.theme.BlueAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        // Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (downloadProgress == null) {
                                // "Later" / snooze button
                                ShadcnButton(
                                    onClick = {
                                        if (downloadedFile == null && !info.forceShow) {
                                            UpdateManager.snoozeUpdate(context, info.version)
                                        }
                                        updateInfo = null
                                        downloadedFile = null
                                    },
                                    variant = ShadcnButtonVariant.Outline,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Later")
                                }

                                // "Download" / "Install" button
                                ShadcnButton(
                                    onClick = {
                                        val file = downloadedFile
                                        if (file != null) {
                                            UpdateManager.triggerInstall(context, file)
                                        } else {
                                            // Trigger background download
                                            downloadProgress = 0.0f
                                            coroutineScope.launch {
                                                UpdateManager.downloadApk(
                                                    context = context,
                                                    downloadUrl = info.downloadUrl,
                                                    onProgress = { p -> downloadProgress = p },
                                                    onSuccess = { apk ->
                                                        downloadProgress = null
                                                        downloadedFile = apk
                                                        UpdateManager.triggerInstall(context, apk)
                                                    },
                                                    onError = { err ->
                                                        downloadProgress = null
                                                        Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                                    }
                                                )
                                            }
                                        }
                                    },
                                    variant = ShadcnButtonVariant.Primary,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(if (downloadedFile != null) "Install" else "Download")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Onboarding Permission Splash Screen
@Composable
private fun PermissionRequiredScreen(
    onRequestPermission: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp)
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "PixelVault",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(12.dp))
        
        Text(
            text = "Secure. Sleek. Native.",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.primary,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            text = "To catalog and render your local images and videos in our high-performance shadcn-inspired layout, PixelVault requires permission to query your device storage media.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        
        Spacer(Modifier.height(48.dp))
        
        ShadcnButton(
            onClick = onRequestPermission,
            variant = ShadcnButtonVariant.Primary,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("Grant Media Permission", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        }
    }
}

private data class NavigationTab(
    val label: String,
    val icon: ImageVector
)

private fun hasRequiredPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}
