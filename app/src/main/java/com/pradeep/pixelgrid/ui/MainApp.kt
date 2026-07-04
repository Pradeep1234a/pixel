package com.pradeep.pixelgrid.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pradeep.pixelgrid.data.MediaItem
import com.pradeep.pixelgrid.data.MediaRepository
import com.pradeep.pixelgrid.ui.components.ShadcnButton
import com.pradeep.pixelgrid.ui.components.ShadcnButtonVariant
import com.pradeep.pixelgrid.ui.screens.*
import kotlinx.coroutines.launch

private const val PREFS_NAME = "pixelvault_settings"
private const val KEY_COLUMNS = "grid_columns"

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

    // Navigation and screen focus states
    var currentTab by remember { mutableStateOf(0) } // 0: Photos, 1: Albums, 2: Favorites, 3: Settings
    var activeViewerItem by remember { mutableStateOf<MediaItem?>(null) }

    // Refresh function to reload MediaStore content
    val refreshMedia = {
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

    // Initial load
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            refreshMedia()
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
                        .padding(paddingValues)
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
                                onMediaClick = { activeViewerItem = it },
                                onRefresh = refreshMedia
                            )
                            1 -> AlbumsScreen(
                                mediaList = mediaList,
                                gridColumns = gridColumns,
                                onMediaClick = { activeViewerItem = it }
                            )
                            2 -> FavoritesScreen(
                                mediaList = mediaList,
                                gridColumns = gridColumns,
                                onMediaClick = { activeViewerItem = it }
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
                                    totalCount = mediaList.size,
                                    totalSize = totalSize
                                )
                            }
                        }
                    }
                }
            }

            // --- FULL SCREEN OVERLAY VIEW SCREEN ---
            AnimatedVisibility(
                visible = activeViewerItem != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                activeViewerItem?.let { item ->
                    // Re-fetch favorites status on render
                    val favoriteIds = remember(mediaList) { MediaRepository.getFavoriteIds(context) }
                    val currentItemWithFavorite = item.copy(isFavorite = favoriteIds.contains(item.id))

                    ViewerScreen(
                        item = currentItemWithFavorite,
                        onBack = { activeViewerItem = null },
                        onFavoriteToggle = { clickedItem ->
                            MediaRepository.toggleFavorite(context, clickedItem.id)
                            refreshMedia() // reload items
                        }
                    )
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
