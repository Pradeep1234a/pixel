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
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateCentroid
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
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
import com.pradeep.pixelgrid.data.RectBounds
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
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
    clickedBounds: RectBounds? = null,
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
    var verticalOffsetY by remember { mutableStateOf(0f) }
    val dragDismissFraction by remember {
        derivedStateOf { (abs(verticalOffsetY) / 1000f).coerceIn(0f, 1f) }
    }

    // Exit transition progress (1f to 0f) for shared-element bounds anim
    var isExiting by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(1f) }

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
            coroutineScope.launch {
                androidx.compose.animation.core.animate(
                    initialValue = verticalOffsetY,
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ) { value, _ ->
                    verticalOffsetY = value
                }
            }
            exitProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            onBack()
        }
    }

    // Set up horizontal pager state
    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { mediaList.size })

    // Track scroll state for each page item's details view dynamically
    val pageScrollStates = remember { mutableStateMapOf<Int, ScrollState>() }
    val activeScrollState = pageScrollStates.getOrPut(pagerState.currentPage) { ScrollState(0) }

    // Intercept back button: scroll details back to top first, then trigger exit transition
    BackHandler(enabled = true) {
        if (activeScrollState.value > 0) {
            coroutineScope.launch {
                activeScrollState.animateScrollTo(0, tween(300))
            }
        } else {
            isExiting = true
        }
    }

    // Find the currently active item
    val activeItem = mediaList.getOrNull(pagerState.currentPage) ?: return

    // Reset zoom and vertical drag offset on page change
    LaunchedEffect(pagerState.currentPage) {
        isZoomed = false
        verticalOffsetY = 0f
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
    val currentProgress = enterProgress.value * exitProgress.value * (1f - dragDismissFraction)
    val finalBgColor = animatedBgColor.copy(alpha = currentProgress.coerceIn(0f, 1f))

    // Screen dimensions in pixels for shared-element bounds math
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

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
            userScrollEnabled = !isZoomed && activeScrollState.value == 0, // Lock horizontal paging when zoomed or scrolled into details
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp
        ) { page ->
            val pageItem = mediaList.getOrNull(page)
            if (pageItem != null) {
                val scrollState = pageScrollStates.getOrPut(page) { ScrollState(0) }
                val configuration = LocalConfiguration.current
                val screenHeight = configuration.screenHeightDp.dp

                val nestedScrollConnection = remember(isZoomed, page) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (isZoomed || page != pagerState.currentPage) return Offset.Zero
                            if (verticalOffsetY > 0f) {
                                val newOffset = verticalOffsetY + available.y
                                return if (newOffset > 0f) {
                                    verticalOffsetY = newOffset
                                    Offset(0f, available.y)
                                } else {
                                    val consumedY = -verticalOffsetY
                                    verticalOffsetY = 0f
                                    Offset(0f, consumedY)
                                }
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(
                            consumed: Offset,
                            available: Offset,
                            source: NestedScrollSource
                        ): Offset {
                            if (isZoomed || page != pagerState.currentPage) return Offset.Zero
                            if (available.y > 0f && scrollState.value == 0) {
                                verticalOffsetY += available.y
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(nestedScrollConnection)
                        .verticalScroll(scrollState)
                        .pointerInput(isZoomed, page) {
                            if (!isZoomed && page == pagerState.currentPage) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val anyPressed = event.changes.any { it.pressed }
                                        if (!anyPressed && verticalOffsetY > 0f) {
                                            if (verticalOffsetY > 220f) {
                                                isExiting = true
                                            } else {
                                                coroutineScope.launch {
                                                    androidx.compose.animation.core.animate(
                                                        initialValue = verticalOffsetY,
                                                        targetValue = 0f,
                                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                                    ) { value, _ ->
                                                        verticalOffsetY = value
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // 1. Fullscreen Image/Video box container
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(screenHeight)
                            .graphicsLayer {
                                val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                
                                // Calculate dynamic entry/exit shared bounds transition!
                                if (clickedBounds != null && page == pagerState.currentPage) {
                                    val thumbCenterX = clickedBounds.left.toFloat() + clickedBounds.width.toFloat() / 2f
                                    val thumbCenterY = clickedBounds.top.toFloat() + clickedBounds.height.toFloat() / 2f
                                    val screenCenterX = screenWidthPx / 2f
                                    val screenCenterY = screenHeightPx / 2f
                                    
                                    val targetTranslationX = thumbCenterX - screenCenterX
                                    val targetTranslationY = thumbCenterY - screenCenterY
                                    
                                    val targetScale = clickedBounds.width.toFloat() / screenWidthPx
                                    val p = currentProgress
                                    val finalScale = targetScale + (1f - targetScale) * p
                                    
                                    scaleX = finalScale
                                    scaleY = finalScale
                                    translationX = targetTranslationX * (1f - p)
                                    translationY = (targetTranslationY * (1f - p)) + verticalOffsetY
                                } else {
                                    // Fallback
                                    translationY = verticalOffsetY
                                    val dragScale = (1f - (abs(verticalOffsetY) / 3000f)).coerceIn(0.85f, 1f)
                                    val pagerScale = 1f - (abs(pageOffset) * 0.12f).coerceIn(0f, 0.12f)
                                    val enterScale = 0.65f + (0.35f * enterProgress.value)
                                    val finalScale = dragScale * exitProgress.value * pagerScale * enterScale
                                    scaleX = finalScale
                                    scaleY = finalScale
                                }
                                
                                val pagerAlpha = 1f - (abs(pageOffset) * 0.45f).coerceIn(0f, 0.45f)
                                alpha = (1f - dragDismissFraction) * pagerAlpha * enterProgress.value * exitProgress.value
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

                    // 2. Google Photos Style details section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E22))
                            .padding(horizontal = 24.dp, vertical = 28.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
                                .align(Alignment.CenterHorizontally)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date(pageItem.dateAdded * 1000)),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(pageItem.dateAdded * 1000)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Details",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp)
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Title", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                                    Text(pageItem.name, style = MaterialTheme.typography.bodyMedium, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Details", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                                    val sizeMB = String.format(Locale.getDefault(), "%.2f MB", pageItem.size / (1024f * 1024f))
                                    val resString = if (pageItem.width > 0 && pageItem.height > 0) "${pageItem.width} × ${pageItem.height}" else ""
                                    Text("$sizeMB  •  $resString  •  ${pageItem.mimeType.substringAfter("/")}", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Path", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.4f))
                                    Text(pageItem.path, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
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
                    val scrollFadeAlpha = (1f - activeScrollState.value.toFloat() / 300f).coerceIn(0f, 1f)
                    alpha = ((1f - dragDismissFraction * 3f) * scrollFadeAlpha).coerceIn(0f, 1f)
                    translationY = -dragDismissFraction * 150f - (activeScrollState.value.toFloat() * 0.5f).coerceAtMost(150f)
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
            visible = showUi,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .graphicsLayer {
                    val scrollFadeAlpha = (1f - activeScrollState.value.toFloat() / 300f).coerceIn(0f, 1f)
                    alpha = ((1f - dragDismissFraction * 3f) * scrollFadeAlpha).coerceIn(0f, 1f)
                    translationY = dragDismissFraction * 150f + (activeScrollState.value.toFloat() * 0.5f).coerceAtMost(150f)
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
                        onClick = {
                            coroutineScope.launch {
                                activeScrollState.animateScrollTo(activeScrollState.maxValue, tween(400))
                            }
                        },
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
    }
}

// Custom transform gesture detector that supports simultaneous pan, zoom, and rotation
// and bubbles single-finger scrolls up to the pager/vertical details view when not zoomed.
private suspend fun PointerInputScope.detectPremiumTransformGestures(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit,
    onGestureEnd: () -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var isTransforming = false
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val rotation = event.calculateRotation()
                
                if (event.changes.size >= 2 || zoom != 1f || rotation != 0f || pan != Offset.Zero) {
                    isTransforming = true
                    event.changes.forEach { it.consume() }
                    val centroid = event.calculateCentroid()
                    onGesture(centroid, pan, zoom, rotation)
                }
            }
        } while (event.changes.any { it.pressed })
        if (isTransforming) {
            onGestureEnd()
        }
    }
}

// Pinch-to-zoom, rotate, and pan interactive ImageViewer with spring settle animations
@Composable
private fun ImageViewer(
    uri: Uri,
    name: String,
    onTap: () -> Unit,
    onScaleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    val rotationAnim = remember { Animatable(0f) }

    val currentScale = scaleAnim.value
    val currentOffsetX = offsetXAnim.value
    val currentOffsetY = offsetYAnim.value
    val currentRotation = rotationAnim.value

    val isCurrentlyZoomed = currentScale > 1.05f
    LaunchedEffect(isCurrentlyZoomed) {
        onScaleChanged(isCurrentlyZoomed)
    }

    // Dynamic on-demand loading of the original resolution bitmap to preserve absolute sharpness up to 10x
    val loadHighRes = currentScale > 1.05f
    val highResRequest = remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            .size(coil.size.Size.ORIGINAL)
            .crossfade(true)
            .build()
    }

    // Dynamic ColorFilter to progressively enhance clarity, contrast, and edge sharpness at higher zoom levels
    val progressiveDetailFilter = remember(currentScale) {
        if (currentScale > 1.05f) {
            val scaleDiff = currentScale - 1f
            // Progressively adjust contrast and saturation to make fine details pop
            val contrast = 1f + 0.035f * scaleDiff.coerceIn(0f, 9f) // up to +31.5% contrast enhancement at 10x zoom
            val saturation = 1f + 0.015f * scaleDiff.coerceIn(0f, 9f) // up to +13.5% saturation for detail visibility
            
            val matrix = ColorMatrix().apply { setToSaturation(saturation) }
            val t = (1f - contrast) / 2f
            val contrastMatrix = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, t * 255f,
                    0f, contrast, 0f, 0f, t * 255f,
                    0f, 0f, contrast, 0f, t * 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            matrix *= contrastMatrix
            ColorFilter.colorMatrix(matrix)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { containerSize = it.size }
            .graphicsLayer(
                scaleX = currentScale,
                scaleY = currentScale,
                translationX = currentOffsetX,
                translationY = currentOffsetY,
                rotationZ = currentRotation
            )
            .pointerInput(Unit) {
                detectPremiumTransformGestures(
                    onGesture = { _, pan, zoom, rotationChange ->
                        coroutineScope.launch {
                            val nextScale = (scaleAnim.value * zoom).coerceIn(0.5f, 15f)
                            scaleAnim.snapTo(nextScale)
                            rotationAnim.snapTo(rotationAnim.value + rotationChange)
                            offsetXAnim.snapTo(offsetXAnim.value + pan.x)
                            offsetYAnim.snapTo(offsetYAnim.value + pan.y)
                        }
                    },
                    onGestureEnd = {
                        coroutineScope.launch {
                            val rawRotation = rotationAnim.value
                            val snappedRotation = (rawRotation / 90f).roundToInt() * 90f
                            
                            val targetScale = when {
                                scaleAnim.value < 1f -> 1f
                                scaleAnim.value > 10f -> 10f
                                else -> scaleAnim.value
                            }
                            
                            val boxWidth = containerSize.width.toFloat()
                            val boxHeight = containerSize.height.toFloat()
                            
                            val maxOffsetX = (boxWidth * (targetScale - 1f)).coerceAtLeast(0f) / 2f
                            val maxOffsetY = (boxHeight * (targetScale - 1f)).coerceAtLeast(0f) / 2f
                            
                            val targetOffsetX = offsetXAnim.value.coerceIn(-maxOffsetX, maxOffsetX)
                            val targetOffsetY = offsetYAnim.value.coerceIn(-maxOffsetY, maxOffsetY)
                            
                            launch {
                                scaleAnim.animateTo(
                                    targetValue = targetScale,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioLowBouncy
                                    )
                                )
                            }
                            launch {
                                offsetXAnim.animateTo(
                                    targetValue = if (targetScale == 1f) 0f else targetOffsetX,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            launch {
                                offsetYAnim.animateTo(
                                    targetValue = if (targetScale == 1f) 0f else targetOffsetY,
                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                )
                            }
                            launch {
                                rotationAnim.animateTo(
                                    targetValue = snappedRotation,
                                    animationSpec = spring(
                                        stiffness = Spring.StiffnessMediumLow,
                                        dampingRatio = Spring.DampingRatioLowBouncy
                                    )
                                )
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        coroutineScope.launch {
                            if (scaleAnim.value > 1f) {
                                launch { scaleAnim.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetXAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { offsetYAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { rotationAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            } else {
                                launch { scaleAnim.animateTo(3f, spring(stiffness = Spring.StiffnessMediumLow)) }
                                launch { rotationAnim.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow)) }
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Base screen-fit image loaded instantly (memory-light)
        AsyncImage(
            model = uri,
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            colorFilter = progressiveDetailFilter
        )
        
        // Original size high-res image layered on top when zoomed
        if (loadHighRes) {
            AsyncImage(
                model = highResRequest,
                contentDescription = name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                colorFilter = progressiveDetailFilter
            )
        }
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
