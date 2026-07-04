package com.pradeep.pixelgrid.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.pradeep.pixelgrid.data.MediaItem
import com.pradeep.pixelgrid.ui.components.ShadcnCard
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@ExperimentalFoundationApi
@Composable
fun ViewerScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    onBack: () -> Unit,
    onFavoriteToggle: (MediaItem) -> Unit,
    onDeleteMedia: (MediaItem) -> Unit,
    videoAutoplay: Boolean = true,
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showUi by remember { mutableStateOf(true) }
    var showInfoDrawer by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }

    // Set up horizontal pager state
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { mediaList.size })

    // Find the currently active item
    val activeItem = mediaList.getOrNull(pagerState.currentPage) ?: return

    // Lazy list state for the bottom thumbnail strip
    val lazyListState = rememberLazyListState()

    // Auto-scroll the thumbnail strip to follow pager swipes
    LaunchedEffect(pagerState.currentPage) {
        if (mediaList.isNotEmpty()) {
            lazyListState.animateScrollToItem(pagerState.currentPage)
        }
        isZoomed = false // Reset zoom state on page change
    }

    // Dynamic system bars configuration to hide/show them in fullscreen mode
    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as android.app.Activity).window
        DisposableEffect(showUi, darkTheme) {
            val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, view)
            
            // Match system theme when UI is visible. If fullscreen (UI hidden), force dark background (white icons)
            val lightStatus = if (showUi) !darkTheme else false
            val lightNav = if (showUi) !darkTheme else false
            
            insetsController.isAppearanceLightStatusBars = lightStatus
            insetsController.isAppearanceLightNavigationBars = lightNav
            
            if (showUi) {
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            } else {
                insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            
            onDispose {
                // Restore default theme-based system bar icons when leaving viewer screen
                val rootController = androidx.core.view.WindowCompat.getInsetsController(window, view)
                rootController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                rootController.isAppearanceLightStatusBars = !darkTheme
                rootController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Media file sharing intent for the active item
    val shareMedia = {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, activeItem.uri)
            type = activeItem.mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media"))
    }

    // Dynamic animated background color fading between dark zinc (UI showing) and black fullscreen
    val targetBgColor = if (showUi) {
        Color(0xFF09090B) // Dark Zinc for contrast focus
    } else {
        Color.Black // Pitch Black
    }
    val animatedBgColor by animateColorAsState(targetBgColor, animationSpec = tween(300))

    // Dynamic layout paddings to center image below header / above footer in normal mode, expanding edge-to-edge in fullscreen
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    
    val targetTopPadding = if (showUi) 56.dp + statusBarHeight else 0.dp
    val targetBottomPadding = if (showUi) 108.dp + navBarHeight else 0.dp
    
    val animatedTopPadding by animateDpAsState(targetTopPadding, animationSpec = tween(300))
    val animatedBottomPadding by animateDpAsState(targetBottomPadding, animationSpec = tween(300))

    // Theme content colors
    val headerColor = if (darkTheme) Color.Black.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surface
    val headerContentColor = if (darkTheme) Color.White else MaterialTheme.colorScheme.onSurface
    val footerColor = if (darkTheme) Color.Black.copy(alpha = 0.8f) else MaterialTheme.colorScheme.surface
    val footerContentColor = if (darkTheme) Color.White else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBgColor)
    ) {
        // --- HORIZONTAL PAGER CONTENT ---
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed, // Lock scrolling when active image is zoomed
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp
        ) { page ->
            val pageItem = mediaList.getOrNull(page)
            if (pageItem != null) {
                // Image container padded to start below header/above footer in normal mode, expanding to full screen smoothly
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = animatedTopPadding, bottom = animatedBottomPadding)
                ) {
                    if (pageItem.isVideo) {
                        VideoPlayer(
                            uri = pageItem.uri,
                            autoplay = videoAutoplay && page == pagerState.currentPage,
                            onTap = { showUi = !showUi },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ImageViewer(
                            uri = pageItem.uri,
                            name = pageItem.name,
                            onTap = { showUi = !showUi },
                            onScaleChanged = { zoomed ->
                                if (page == pagerState.currentPage) {
                                    isZoomed = zoomed
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // --- TOP ACTION BAR & CONTROL OVERLAY ---
        AnimatedVisibility(
            visible = showUi,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                color = headerColor,
                contentColor = headerContentColor,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = headerContentColor)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = formatHeaderDate(activeItem.dateAdded),
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                            fontWeight = FontWeight.Bold,
                            color = headerContentColor
                        )
                        Text(
                            text = formatHeaderTime(activeItem.dateAdded),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = headerContentColor.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // --- BOTTOM PANEL OVERLAY (Carousel Strip + Action Bar) ---
        AnimatedVisibility(
            visible = showUi && !showInfoDrawer,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(footerColor)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Horizontal Previews Strip
                LazyRow(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    itemsIndexed(mediaList) { index, item ->
                        val isSelected = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(
                                    width = if (isSelected) 2.dp else 0.dp,
                                    color = if (isSelected) footerContentColor else Color.Transparent,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // 2. Control Action Buttons Row (Share, Favorite, Delete, Info)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = shareMedia) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = footerContentColor)
                    }
                    IconButton(onClick = { onFavoriteToggle(activeItem) }) {
                        Icon(
                            imageVector = if (activeItem.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (activeItem.isFavorite) Color.Red else footerContentColor
                        )
                    }
                    IconButton(onClick = { onDeleteMedia(activeItem) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = footerContentColor)
                    }
                    IconButton(onClick = { showInfoDrawer = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = footerContentColor)
                    }
                }
            }
        }

        // --- SHADCN INFO DRAWER ---
        AnimatedVisibility(
            visible = showInfoDrawer,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
        ) {
            ShadcnCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "File Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(onClick = { showInfoDrawer = false }) {
                        Text(
                            "Close",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    InfoRow(label = "Filename", value = activeItem.name)
                    InfoRow(label = "Folder", value = activeItem.bucketName)
                    InfoRow(label = "Type", value = activeItem.mimeType)
                    InfoRow(label = "Size", value = formatSize(activeItem.size))
                    if (activeItem.width > 0 && activeItem.height > 0) {
                        InfoRow(label = "Resolution", value = "${activeItem.width} x ${activeItem.height}")
                    }
                    InfoRow(label = "Date Modified", value = formatFullDate(activeItem.dateAdded))
                    InfoRow(label = "File Path", value = activeItem.path)
                }
            }
        }
    }
}

// Custom detail row styled minimally
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(2f)
        )
    }
}

// Pinch-to-zoom interactive ImageViewer with zoom lock callback
@Composable
private fun ImageViewer(
    uri: Uri,
    name: String,
    onTap: () -> Unit,
    onScaleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                            onScaleChanged(false)
                        } else {
                            scale = 2.5f
                            onScaleChanged(true)
                        }
                    }
                )
            }
    ) {
        AsyncImage(
            model = uri,
            contentDescription = name,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        if (scale > 1.05f) {
                            offset += pan
                            onScaleChanged(true)
                        } else {
                            scale = 1f
                            offset = Offset.Zero
                            onScaleChanged(false)
                        }
                    }
                },
            contentScale = ContentScale.Fit
        )
    }
}

// Media3 / ExoPlayer Video player
@Composable
private fun VideoPlayer(
    uri: Uri,
    autoplay: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = Media3Item.fromUri(uri)
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = autoplay
        }
    }

    // React to active page changed autoplay toggles
    LaunchedEffect(autoplay) {
        exoPlayer.playWhenReady = autoplay
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    setBackgroundColor(android.graphics.Color.BLACK)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

// Helpers to format timestamp to date string in title
private fun formatHeaderDate(timestampSeconds: Long): String {
    val date = Date(timestampSeconds * 1000)
    return SimpleDateFormat("d MMMM", Locale.getDefault()).format(date)
}

private fun formatHeaderTime(timestampSeconds: Long): String {
    val date = Date(timestampSeconds * 1000)
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
}

// Helper to format bytes to readable size
private fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    return String.format(
        Locale.getDefault(),
        "%.2f %s",
        bytes / Math.pow(1024.0, digitGroups.toDouble()),
        units.getOrElse(digitGroups) { "GB" }
    )
}

// Helper to format timestamp to date string
private fun formatFullDate(timestampSeconds: Long): String {
    val date = Date(timestampSeconds * 1000)
    return SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(date)
}
