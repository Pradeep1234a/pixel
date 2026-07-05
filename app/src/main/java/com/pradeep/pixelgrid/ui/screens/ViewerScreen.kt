package com.pradeep.pixelgrid.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import android.widget.Toast
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
import androidx.compose.ui.input.pointer.PointerInputScope
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
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    var showUi by remember { mutableStateOf(true) }
    var showInfoDrawer by remember { mutableStateOf(false) }
    var isZoomed by remember { mutableStateOf(false) }
    var dragDismissFraction by remember { mutableStateOf(0f) }

    // Exit transition states for buttery motion on close/back press
    var isExiting by remember { mutableStateOf(false) }
    var exitOffsetY by remember { mutableStateOf(0f) }
    var exitScale by remember { mutableStateOf(1f) }
    var exitAlpha by remember { mutableStateOf(1f) }

    // Grid-to-detail shared-element expansion progress
    val enterProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enterProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                stiffness = Spring.StiffnessMediumLow,
                dampingRatio = Spring.DampingRatioLowBouncy
            )
        )
    }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            showUi = false
            showInfoDrawer = false
            val animOffset = Animatable(exitOffsetY)
            val animScale = Animatable(exitScale)
            val animAlpha = Animatable(exitAlpha)
            
            coroutineScope.launch {
                animOffset.animateTo(
                    targetValue = exitOffsetY + 900f, // slide down offscreen
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                ) {
                    exitOffsetY = value
                }
            }
            coroutineScope.launch {
                animScale.animateTo(
                    targetValue = 0.82f, // scale down
                    animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                ) {
                    exitScale = value
                }
            }
            coroutineScope.launch {
                animAlpha.animateTo(
                    targetValue = 0f, // fade out
                    animationSpec = tween(durationMillis = 240, easing = LinearEasing)
                ) {
                    exitAlpha = value
                }
            }
            delay(270)
            onBack()
        }
    }

    // Intercept system back button/gesture to prevent app exit, returning to grid instead
    BackHandler(enabled = true) {
        if (showInfoDrawer) {
            showInfoDrawer = false
        } else {
            isExiting = true
        }
    }

    // Set up horizontal pager state
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { mediaList.size })

    // Find the currently active item
    val activeItem = mediaList.getOrNull(pagerState.currentPage) ?: return

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

    // Media edit intent invoking standard photo editor
    val editMedia = {
        try {
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(activeItem.uri, activeItem.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Edit Media"))
        } catch (e: Exception) {
            Toast.makeText(context, "No editor available for this file type", Toast.LENGTH_SHORT).show()
        }
    }

    // Google Lens trigger intent with fallback browser view
    val launchGoogleLens = {
        try {
            val intent = Intent("com.google.android.gms.lens.LAUNCH_FROM_API").apply {
                setDataAndType(activeItem.uri, activeItem.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra("lens_launch_status_panel", true)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://lens.google.com/uploadbyurl?url=${activeItem.uri}"))
                context.startActivity(intent)
            } catch (ex: Exception) {
                Toast.makeText(context, "Google Lens is not installed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Dynamic animated background color fading between dark zinc (UI showing) and black fullscreen
    val targetBgColor = if (showUi) {
        Color(0xFF09090B) // Dark Zinc for contrast focus
    } else {
        Color.Black // Pitch Black
    }
    val animatedBgColor by animateColorAsState(targetBgColor, animationSpec = tween(300))
    val finalBgColor = animatedBgColor.copy(alpha = ((1f - dragDismissFraction) * exitAlpha).coerceIn(0f, 1f))

    // Theme content colors: overlays are transparent to let photo show edge-to-edge behind them
    val headerColor = Color.Transparent
    val headerContentColor = Color.White
    val footerColor = Color.Transparent
    val footerContentColor = Color.White

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(finalBgColor)
    ) {
        // --- HORIZONTAL PAGER CONTENT ---
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed && !showInfoDrawer, // Lock scrolling when active image is zoomed or details sheet is open
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp
        ) { page ->
            val pageItem = mediaList.getOrNull(page)
            if (pageItem != null) {
                var verticalOffsetY by remember { mutableStateOf(0f) }

                // Image container remains locked in full screen bounds (zero layout shifts on UI changes)
                // Added vertical swipe-to-dismiss (drag down) and swipe-to-info (drag up) gestures
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(isZoomed) {
                            if (!isZoomed) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        verticalOffsetY += dragAmount
                                        dragDismissFraction = (abs(verticalOffsetY) / 1000f).coerceIn(0f, 1f)
                                    },
                                    onDragEnd = {
                                        if (verticalOffsetY > 220f) {
                                            isExiting = true // Trigger smooth exit animation instead of jumping
                                        } else if (verticalOffsetY < -220f) {
                                            showInfoDrawer = true
                                            coroutineScope.launch {
                                                androidx.compose.animation.core.animate(
                                                    initialValue = verticalOffsetY,
                                                    targetValue = 0f,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                                ) { value, _ ->
                                                    verticalOffsetY = value
                                                    dragDismissFraction = (abs(value) / 1000f).coerceIn(0f, 1f)
                                                }
                                            }
                                        } else {
                                            coroutineScope.launch {
                                                androidx.compose.animation.core.animate(
                                                    initialValue = verticalOffsetY,
                                                    targetValue = 0f,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                                ) { value, _ ->
                                                    verticalOffsetY = value
                                                    dragDismissFraction = (abs(value) / 1000f).coerceIn(0f, 1f)
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        coroutineScope.launch {
                                            androidx.compose.animation.core.animate(
                                                initialValue = verticalOffsetY,
                                                targetValue = 0f,
                                                animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                              ) { value, _ ->
                                                verticalOffsetY = value
                                                dragDismissFraction = (abs(value) / 1000f).coerceIn(0f, 1f)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                        .graphicsLayer {
                            // Calculate current page scrolling translation offset
                            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                            
                            translationY = verticalOffsetY + exitOffsetY
                            
                            // 1. Combine drag-to-dismiss scale, exit transition scale, horizontal pager swipe scale, and enter progress scale
                            val dragScale = (1f - (abs(verticalOffsetY) / 3000f)).coerceIn(0.85f, 1f)
                            val pagerScale = 1f - (abs(pageOffset) * 0.12f).coerceIn(0f, 0.12f) // smooth scale down as page moves away
                            val enterScale = 0.65f + (0.35f * enterProgress.value) // expand from center coordinates
                            val finalScale = dragScale * exitScale * pagerScale * enterScale
                            scaleX = finalScale
                            scaleY = finalScale
                            
                            // 2. Combine swipe-down drag opacity, exit animation fade, horizontal swipe page fade, and enter fade
                            val pagerAlpha = 1f - (abs(pageOffset) * 0.45f).coerceIn(0f, 0.45f) // smooth fade out as page moves away
                            alpha = exitAlpha * (1f - dragDismissFraction) * pagerAlpha * enterProgress.value
                        }
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    alpha = (1f - dragDismissFraction * 3f).coerceIn(0f, 1f)
                    translationY = -dragDismissFraction * 150f
                }
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
                    IconButton(onClick = { isExiting = true }) {
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

        // --- BOTTOM PANEL OVERLAY (Action Bar & Lens Chip) ---
        AnimatedVisibility(
            visible = showUi && !showInfoDrawer,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .graphicsLayer {
                    alpha = (1f - dragDismissFraction * 3f).coerceIn(0f, 1f)
                    translationY = dragDismissFraction * 150f
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(footerColor)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // --- Premium Bottom Filmstrip ---
                val itemWidthDp = 50.dp
                val spacingDp = 6.dp
                val itemWidthPx = with(density) { (itemWidthDp + spacingDp).toPx() }
                val halfItemWidthPx = with(density) { (itemWidthDp / 2).toPx() }
                val configuration = LocalConfiguration.current
                val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                
                // Continuous scroll offset of the filmstrip centered on the active item
                val currentScrollPos = pagerState.currentPage + pagerState.currentPageOffsetFraction
                
                // Local window optimization to render 21 items around current index for butter-smooth rendering
                val startIndex = (pagerState.currentPage - 10).coerceAtLeast(0)
                val windowedList = mediaList.drop(startIndex).take(21)
                
                val filmstripTranslationX = (screenWidthPx / 2f) - halfItemWidthPx - (currentScrollPos * itemWidthPx) + (startIndex * itemWidthPx)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier
                            .graphicsLayer { translationX = filmstripTranslationX }
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        windowedList.forEachIndexed { relativeIndex, item ->
                            val index = startIndex + relativeIndex
                            val isSelected = index == pagerState.currentPage && !pagerState.isScrollInProgress
                            
                            val thumbScale by animateFloatAsState(
                                targetValue = if (isSelected) 1.25f else 1.0f,
                                animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                            )
                            val borderAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1.0f else 0f,
                                animationSpec = tween(200)
                            )

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp) // 6.dp spacing total
                                    .size(itemWidthDp)
                                    .graphicsLayer {
                                        scaleX = thumbScale
                                        scaleY = thumbScale
                                    }
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(
                                        width = 2.dp,
                                        color = Color.White.copy(alpha = borderAlpha),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(item.uri)
                                        .videoFrameMillis(1000)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Filmstrip Thumbnail",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
                // 1. Google Lens Pill Chip (floats on the right side above action bar)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .clickable { launchGoogleLens() }
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(18.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = LensIcon,
                                contentDescription = "Google Lens",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Google Lens",
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // 2. Control Action Buttons Row (Share, Pill Action Bar, Info) matching system gallery
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Share Button (circular translucent)
                    IconButton(
                        onClick = shareMedia,
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                    }

                    // Middle Pill Action Bar (translucent pill containing Favorite, Edit, Delete)
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier
                            .height(46.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(onClick = { onFavoriteToggle(activeItem) }) {
                                Icon(
                                    imageVector = if (activeItem.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (activeItem.isFavorite) Color.Red else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = editMedia) {
                                Icon(
                                    imageVector = EditIcon,
                                    contentDescription = "Edit",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(onClick = { onDeleteMedia(activeItem) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Info Button (circular translucent)
                    IconButton(
                        onClick = { showInfoDrawer = true },
                        modifier = Modifier
                            .size(46.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // --- GOOGLE PHOTOS STYLE DETAILS SHEET ---
        val sheetHeight = 460.dp
        val targetSheetOffsetY = if (showInfoDrawer) 0f else with(density) { sheetHeight.toPx() }
        var dragSheetOffset by remember { mutableStateOf(0f) }
        val animatedSheetOffsetY by animateFloatAsState(
            targetValue = targetSheetOffsetY + dragSheetOffset,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, animatedSheetOffsetY.roundToInt()) }
                .background(
                    color = Color(0xEE121214), // Premium dark glass
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            // Drag downwards to dismiss details
                            dragSheetOffset = (dragSheetOffset + dragAmount).coerceAtLeast(0f)
                        },
                        onDragEnd = {
                            if (dragSheetOffset > 150f) {
                                showInfoDrawer = false
                            }
                            dragSheetOffset = 0f
                        },
                        onDragCancel = {
                            dragSheetOffset = 0f
                        }
                    )
                }
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Drag indicator gray bar
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, bottom = 16.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 28.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

// Custom transform gesture detector that only consumes pointer events during pinch zoom (2+ fingers)
// or when the image is already zoomed, letting single-finger horizontal swipes bubble up to HorizontalPager
private suspend fun PointerInputScope.detectZoomPanGestures(
    onGesture: (pan: Offset, zoom: Float) -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                
                // Consume event and invoke gesture only if zooming (2+ fingers) or zoom change is active
                if (event.changes.size >= 2 || zoom != 1f) {
                    event.changes.forEach { it.consume() }
                    onGesture(pan, zoom)
                }
            }
        } while (event.changes.any { it.pressed })
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
                .pointerInput(scale) {
                    if (scale > 1f) {
                        // When zoomed in, consume pan/zoom gestures normally
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
                    } else {
                        // When not zoomed, use custom detector to only consume pinch-to-zoom (2+ fingers)
                        // and let single finger drags pass through to HorizontalPager
                        detectZoomPanGestures { pan, zoom ->
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

// Custom vector icon representing Pencil / Edit
private val EditIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Edit",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 2f,
            pathFillType = PathFillType.NonZero
        ) {
            moveTo(3f, 17.25f)
            verticalLineTo(21f)
            horizontalLineTo(6.75f)
            lineTo(17.81f, 9.94f)
            lineTo(14.06f, 6.19f)
            lineTo(3f, 17.25f)
            close()
            moveTo(20.71f, 7.04f)
            curveTo(21.1f, 6.65f, 21.1f, 6.02f, 20.71f, 5.63f)
            lineTo(18.37f, 3.29f)
            curveTo(17.98f, 2.9f, 17.35f, 2.9f, 16.96f, 3.29f)
            lineTo(15.13f, 5.12f)
            lineTo(18.88f, 8.87f)
            lineTo(20.71f, 7.04f)
            close()
        }
    }.build()

// Custom vector icon representing Google Lens frame
private val LensIcon: ImageVector
    get() = ImageVector.Builder(
        name = "Lens",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        // Outer corners
        path(
            fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 2f
        ) {
            moveTo(7f, 3f)
            horizontalLineTo(5f)
            curveTo(3.9f, 3f, 3f, 3.9f, 3f, 5f)
            verticalLineTo(7f)
            
            moveTo(3f, 17f)
            verticalLineTo(19f)
            curveTo(3f, 20.1f, 3.9f, 21f, 5f, 21f)
            horizontalLineTo(7f)
            
            moveTo(17f, 21f)
            horizontalLineTo(19f)
            curveTo(20.1f, 21f, 21f, 20.1f, 21f, 19f)
            verticalLineTo(17f)
            
            moveTo(21f, 7f)
            verticalLineTo(5f)
            curveTo(21f, 3.9f, 20.1f, 3f, 19f, 3f)
            horizontalLineTo(17f)
        }
        // Inner circle
        path(
            fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.White),
            strokeLineWidth = 2f
        ) {
            moveTo(12f, 9f)
            curveTo(10.34f, 9f, 9f, 10.34f, 9f, 12f)
            curveTo(9f, 13.66f, 10.34f, 15f, 12f, 15f)
            curveTo(13.66f, 15f, 15f, 13.66f, 15f, 12f)
            curveTo(15f, 10.34f, 13.66f, 9f, 12f, 9f)
            close()
        }
        // Small dot inside
        path(
            fill = androidx.compose.ui.graphics.SolidColor(Color.White)
        ) {
            moveTo(16.5f, 7.5f)
            curveTo(15.95f, 7.5f, 15.5f, 7.95f, 15.5f, 8.5f)
            curveTo(15.5f, 9.05f, 15.95f, 9.5f, 16.5f, 9.5f)
            curveTo(17.05f, 9.5f, 17.5f, 9.05f, 17.5f, 8.5f)
            curveTo(17.5f, 7.95f, 17.05f, 7.5f, 16.5f, 7.5f)
            close()
        }
    }.build()
