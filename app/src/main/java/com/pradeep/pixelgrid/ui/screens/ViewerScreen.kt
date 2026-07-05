package com.pradeep.pixelgrid.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.pradeep.pixelgrid.data.MediaItem
import com.pradeep.pixelgrid.ui.components.ShadcnCard
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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

    // Filmstrip state
    val filmstripListState = rememberLazyListState()
    var filmstripIsSettled by remember { mutableStateOf(true) }
    var filmstripRowWidthPx by remember { mutableIntStateOf(0) }

    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Filmstrip thumbnail dimensions
    val thumbBaseSizeDp = 40.dp
    val thumbSpacingDp = 4.dp
    val thumbBaseSizePx = with(density) { thumbBaseSizeDp.toPx() }
    val thumbSpacingPx = with(density) { thumbSpacingDp.toPx() }

    // Track scrolling state to implement delayed selection highlight
    LaunchedEffect(pagerState.isScrollInProgress, filmstripListState.isScrollInProgress) {
        val isMoving = pagerState.isScrollInProgress || filmstripListState.isScrollInProgress
        if (isMoving) {
            filmstripIsSettled = false
        } else {
            // Small delay to let the strip fully settle before showing highlight
            delay(200)
            filmstripIsSettled = true
        }
    }

    // Continuous filmstrip sync: tracks pager position during mid-swipe for fluid strip movement
    LaunchedEffect(pagerState, filmstripRowWidthPx) {
        if (mediaList.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            pagerState.currentPage to pagerState.currentPageOffsetFraction
        }.collect { (page, offsetFraction) ->
            if (filmstripRowWidthPx <= 0) return@collect
            val halfScreen = filmstripRowWidthPx / 2f
            val itemCenter = thumbBaseSizePx / 2f
            val baseCenterOffset = (halfScreen - itemCenter).toInt()

            // Calculate fractional scroll offset for smooth mid-swipe tracking
            val itemStride = thumbBaseSizePx + thumbSpacingPx
            val fractionalPixelShift = (offsetFraction * itemStride).toInt()

            // Scroll to current page with fractional offset applied
            filmstripListState.scrollToItem(
                index = page,
                scrollOffset = -baseCenterOffset + fractionalPixelShift
            )
        }
    }

    // Reset zoom on page change
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
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
                // Image container remains locked in full screen bounds (zero layout shifts on UI changes)
                Box(
                    modifier = Modifier.fillMaxSize()
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

        // --- BOTTOM PANEL OVERLAY (Filmstrip + Action Bar) ---
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
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Premium Filmstrip with distance-based scaling
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Calculate content padding to center the first and last items
                    val halfScreenDp = with(density) { (filmstripRowWidthPx / 2f).toDp() }
                    val halfThumbDp = thumbBaseSizeDp / 2
                    val edgePadding = (halfScreenDp - halfThumbDp).coerceAtLeast(0.dp)

                    LazyRow(
                        state = filmstripListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .onGloballyPositioned { coords ->
                                filmstripRowWidthPx = coords.size.width
                            },
                        horizontalArrangement = Arrangement.spacedBy(thumbSpacingDp),
                        contentPadding = PaddingValues(horizontal = edgePadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(mediaList) { index, item ->
                            // Calculate this item's visual center position relative to the LazyRow viewport center
                            val itemInfo = filmstripListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                            val viewportCenter = filmstripRowWidthPx / 2f
                            val distanceFromCenter = if (itemInfo != null) {
                                val itemCenter = itemInfo.offset + itemInfo.size / 2f
                                abs(itemCenter - viewportCenter)
                            } else {
                                // Off-screen: use maximum distance
                                viewportCenter
                            }

                            // Normalize distance: 0 = center, 1 = far from center
                            val maxInfluenceDistance = thumbBaseSizePx * 3f
                            val normalizedDistance = (distanceFromCenter / maxInfluenceDistance).coerceIn(0f, 1f)

                            // Scale: center = 1.35x, edges = 1.0x (compact)
                            val isSelected = pagerState.currentPage == index && filmstripIsSettled
                            val targetScale = if (isSelected) 1.35f else {
                                1f + 0.35f * (1f - normalizedDistance)
                            }
                            val animatedScale by animateFloatAsState(
                                targetValue = if (filmstripIsSettled && isSelected) 1.35f else targetScale,
                                animationSpec = if (filmstripIsSettled) spring(stiffness = Spring.StiffnessLow) else tween(0)
                            )

                            // Border highlight: only when settled
                            val borderAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0f,
                                animationSpec = tween(durationMillis = 250)
                            )

                            Box(
                                modifier = Modifier
                                    .size(thumbBaseSizeDp)
                                    .graphicsLayer {
                                        scaleX = animatedScale
                                        scaleY = animatedScale
                                    }
                                    .clip(RoundedCornerShape(5.dp))
                                    .then(
                                        if (borderAlpha > 0.01f) {
                                            Modifier.border(
                                                width = 2.dp,
                                                color = footerContentColor.copy(alpha = borderAlpha),
                                                shape = RoundedCornerShape(5.dp)
                                            )
                                        } else Modifier
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            ) {
                                val thumbContext = LocalContext.current
                                val imageModel = remember(item) {
                                    if (item.isVideo) {
                                        ImageRequest.Builder(thumbContext)
                                            .data(item.uri)
                                            .videoFrameMillis(1000)
                                            .build()
                                    } else {
                                        item.uri as Any
                                    }
                                }
                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = item.name,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
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
// Gesture handling is carefully structured to not interfere with HorizontalPager swipe
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
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
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
                }
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
