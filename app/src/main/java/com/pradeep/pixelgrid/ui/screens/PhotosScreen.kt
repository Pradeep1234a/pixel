package com.pradeep.pixelgrid.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import kotlin.math.abs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.pradeep.pixelgrid.data.MediaItem
import com.pradeep.pixelgrid.data.RectBounds
import com.pradeep.pixelgrid.data.MediaRepository
import com.pradeep.pixelgrid.ui.components.ShadcnBadge
import com.pradeep.pixelgrid.ui.components.ShadcnButton
import com.pradeep.pixelgrid.ui.components.ShadcnButtonVariant
import com.pradeep.pixelgrid.ui.theme.BlueAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

sealed class BentoRowSpec {
    data class DenseRow(val items: List<Pair<MediaItem, Int>>) : BentoRowSpec()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    mediaList: List<MediaItem>,
    gridColumns: Int,
    layoutMode: String,
    onColumnsChange: (Int) -> Unit,
    onMediaClick: (List<MediaItem>, Int, RectBounds?) -> Unit,
    onRefresh: () -> Unit,
    topPadding: Dp,
    onScrollChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<MediaItem>() }

    // 2. Quick Category Filtering state
    val categories = listOf("All", "Camera", "Screenshots", "Videos", "Favorites", "Downloads")
    var selectedCategory by remember { mutableStateOf("All") }

    // Filter media list by category
    val filteredMediaList = remember(mediaList, selectedCategory) {
        when (selectedCategory) {
            "Camera" -> mediaList.filter { it.path.contains("DCIM/Camera", ignoreCase = true) || it.bucketName.equals("Camera", ignoreCase = true) }
            "Screenshots" -> mediaList.filter { it.path.contains("Screenshot", ignoreCase = true) || it.bucketName.contains("Screenshot", ignoreCase = true) }
            "Videos" -> mediaList.filter { it.isVideo }
            "Favorites" -> mediaList.filter { it.isFavorite }
            "Downloads" -> mediaList.filter { it.path.contains("Download", ignoreCase = true) || it.bucketName.contains("Download", ignoreCase = true) }
            else -> mediaList
        }
    }

    // 3. Quick Preview state
    var previewItem by remember { mutableStateOf<MediaItem?>(null) }

    // 4. Scroll state tracking
    val lazyListState = rememberLazyListState()
    val scrollFraction by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex == 0) {
                (lazyListState.firstVisibleItemScrollOffset.toFloat() / 200f).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }

    LaunchedEffect(scrollFraction) {
        if (!isSelectionMode) {
            onScrollChange(scrollFraction)
        }
    }

    // Reset selection mode if selection matches change or list empty
    LaunchedEffect(filteredMediaList) {
        if (selectedItems.isNotEmpty()) {
            val currentIds = filteredMediaList.map { it.id }.toSet()
            selectedItems.removeAll { !currentIds.contains(it.id) }
            if (selectedItems.isEmpty()) {
                isSelectionMode = false
            }
        }
    }

    // Recoverable security exception launcher for deletions
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onRefresh()
            isSelectionMode = false
            selectedItems.clear()
        }
    }

    // Handle delete action
    val onDeleteSelected: () -> Unit = {
        coroutineScope.launch {
            var requestSender: android.content.IntentSender? = null
            for (item in selectedItems) {
                try {
                    val sender = MediaRepository.deleteMediaItem(context, item)
                    if (sender != null) {
                        requestSender = sender
                        break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (requestSender != null) {
                deleteLauncher.launch(IntentSenderRequest.Builder(requestSender).build())
            } else {
                onRefresh()
                isSelectionMode = false
                selectedItems.clear()
            }
        }
    }

    // Handle share action
    val onShareSelected: () -> Unit = {
        val uris = ArrayList<Uri>().apply {
            addAll(selectedItems.map { it.uri })
        }
        val intent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            type = "image/* video/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Media"))
    }

    // Group filtered media by date for hierarchical display
    val groupedMedia = remember(filteredMediaList) {
        filteredMediaList.groupBy { getHeaderDateString(it.dateAdded) }
    }

    // Precompute dynamic asymmetrical packed Bento rows per date group
    val bentoRows = remember(groupedMedia, gridColumns) {
        groupedMedia.mapValues { (_, items) ->
            packItemsIntoBentoRows(items, gridColumns)
        }
    }



    // Precompute Uniform Square rows
    val squareRows = remember(groupedMedia, gridColumns) {
        groupedMedia.mapValues { (_, items) ->
            items.chunked(gridColumns)
        }
    }

    // Precompute Compact Timeline rows
    val compactRows = remember(groupedMedia) {
        groupedMedia.mapValues { (_, items) ->
            items.chunked(5)
        }
    }

    // Dynamic memories recommendation engine
    val memoryItems = remember(mediaList) {
        val allMemories = mutableListOf<MemoryCardInfo>()
        
        // 1. Favorites memory card
        val favs = mediaList.filter { it.isFavorite }
        if (favs.isNotEmpty()) {
            allMemories.add(
                MemoryCardInfo(
                    id = "favorites",
                    title = "Favorites",
                    subtitle = "Your absolute standout moments",
                    coverUri = favs.first().uri,
                    items = favs.sortedByDescending { it.dateAdded }
                )
            )
        }

        // 2. On This Day memory card
        val calendarToday = Calendar.getInstance()
        val todayMonth = calendarToday.get(Calendar.MONTH)
        val todayDay = calendarToday.get(Calendar.DAY_OF_MONTH)
        val onThisDayPhotos = mediaList.filter {
            val photoCal = Calendar.getInstance().apply { time = Date(it.dateAdded * 1000) }
            photoCal.get(Calendar.MONTH) == todayMonth && photoCal.get(Calendar.DAY_OF_MONTH) == todayDay && photoCal.get(Calendar.YEAR) < calendarToday.get(Calendar.YEAR)
        }
        if (onThisDayPhotos.isNotEmpty()) {
            val yearsAgo = calendarToday.get(Calendar.YEAR) - Calendar.getInstance().apply { time = Date(onThisDayPhotos.first().dateAdded * 1000) }.get(Calendar.YEAR)
            val suffix = if (yearsAgo == 1) "year ago" else "years ago"
            allMemories.add(
                MemoryCardInfo(
                    id = "on_this_day",
                    title = "On This Day",
                    subtitle = "$yearsAgo $suffix today",
                    coverUri = onThisDayPhotos.first().uri,
                    items = onThisDayPhotos.sortedByDescending { it.dateAdded }
                )
            )
        }

        // 3. Trip detection based on high-density capture windows (e.g. 10+ items within 48 hours)
        val sortedMedia = mediaList.sortedBy { it.dateAdded }
        if (sortedMedia.isNotEmpty()) {
            var currentTrip = mutableListOf<MediaItem>()
            var lastTime = 0L
            val trips = mutableListOf<List<MediaItem>>()
            
            for (item in sortedMedia) {
                if (currentTrip.isEmpty()) {
                    currentTrip.add(item)
                    lastTime = item.dateAdded
                } else {
                    if (item.dateAdded - lastTime <= 48 * 3600) {
                        currentTrip.add(item)
                        lastTime = item.dateAdded
                    } else {
                        if (currentTrip.size >= 10) {
                            trips.add(currentTrip)
                        }
                        currentTrip = mutableListOf(item)
                        lastTime = item.dateAdded
                    }
                }
            }
            if (currentTrip.size >= 10) {
                trips.add(currentTrip)
            }

            trips.forEachIndexed { index, tripItems ->
                val startCal = Calendar.getInstance().apply { time = Date(tripItems.first().dateAdded * 1000) }
                val monthName = SimpleDateFormat("MMMM", Locale.getDefault()).format(startCal.time)
                val year = startCal.get(Calendar.YEAR)
                val bucket = tripItems.first().bucketName
                val title = if (bucket.isNotEmpty() && bucket != "0" && bucket != "Camera") "Trip to $bucket" else "Adventure in $monthName"
                allMemories.add(
                    MemoryCardInfo(
                        id = "trip_$index",
                        title = title,
                        subtitle = "$monthName $year (${tripItems.size} items)",
                        coverUri = tripItems.first().uri,
                        items = tripItems.sortedByDescending { it.dateAdded }
                    )
                )
            }
        }

        // 4. Rediscovered Moments (old photos that aren't favorites)
        val oldestMedia = mediaList.sortedBy { it.dateAdded }
        if (oldestMedia.size > 15) {
            val sliceSize = oldestMedia.size / 5
            val candidates = oldestMedia.take(sliceSize).filter { !it.isFavorite }
            if (candidates.isNotEmpty()) {
                val cover = candidates.first()
                allMemories.add(
                    MemoryCardInfo(
                        id = "rediscovered",
                        title = "Rediscovered Moments",
                        subtitle = "A look back at lost captures",
                        coverUri = cover.uri,
                        items = candidates.shuffled(Random(42))
                    )
                )
            }
        }

        // 5. Seasonal Flashback
        val currentMonth = calendarToday.get(Calendar.MONTH)
        val seasonName = when (currentMonth) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "Winter"
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "Spring"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "Summer"
            else -> "Autumn"
        }
        val seasonalItems = mediaList.filter {
            val itemCal = Calendar.getInstance().apply { time = Date(it.dateAdded * 1000) }
            val itemMonth = itemCal.get(Calendar.MONTH)
            val sameSeason = when (seasonName) {
                "Winter" -> itemMonth in listOf(Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY)
                "Spring" -> itemMonth in listOf(Calendar.MARCH, Calendar.APRIL, Calendar.MAY)
                "Summer" -> itemMonth in listOf(Calendar.JUNE, Calendar.JULY, Calendar.AUGUST)
                else -> itemMonth in listOf(Calendar.SEPTEMBER, Calendar.OCTOBER, Calendar.NOVEMBER)
            }
            sameSeason && itemCal.get(Calendar.YEAR) < calendarToday.get(Calendar.YEAR)
        }
        if (seasonalItems.isNotEmpty()) {
            allMemories.add(
                MemoryCardInfo(
                    id = "seasonal",
                    title = "$seasonName Flashback",
                    subtitle = "Relive your $seasonName memories",
                    coverUri = seasonalItems.first().uri,
                    items = seasonalItems.sortedByDescending { it.dateAdded }
                )
            )
        }

        // 6. Weekend Vibing
        val weekendItems = mediaList.filter {
            val itemCal = Calendar.getInstance().apply { time = Date(it.dateAdded * 1000) }
            val dayOfWeek = itemCal.get(Calendar.DAY_OF_WEEK)
            dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY
        }
        if (weekendItems.size >= 5) {
            allMemories.add(
                MemoryCardInfo(
                    id = "weekend",
                    title = "Weekend Highlights",
                    subtitle = "Chill captures and weekend vibes",
                    coverUri = weekendItems.first().uri,
                    items = weekendItems.sortedByDescending { it.dateAdded }
                )
            )
        }

        // 7. Spotlight on Portraits
        val portraits = mediaList.filter {
            val aspect = if (it.width > 0 && it.height > 0) it.width.toFloat() / it.height.toFloat() else 1f
            aspect in 0.55f..0.78f && !it.isVideo
        }
        if (portraits.size >= 5) {
            allMemories.add(
                MemoryCardInfo(
                    id = "portraits",
                    title = "Portrait Spotlights",
                    subtitle = "Standout portrait and selfie captures",
                    coverUri = portraits.first().uri,
                    items = portraits.sortedByDescending { it.dateAdded }
                )
            )
        }

        // --- DYNAMIC ROTATION RECOMMENDATION ENGINE ---
        val daySeed = calendarToday.get(Calendar.DAY_OF_YEAR)
        val rand = Random(daySeed.toLong())
        
        val recommended = mutableListOf<MemoryCardInfo>()
        val pool = allMemories.toMutableList()
        
        val onThisDayCard = pool.find { it.id == "on_this_day" }
        if (onThisDayCard != null) {
            recommended.add(onThisDayCard)
            pool.remove(onThisDayCard)
        }
        val favoritesCard = pool.find { it.id == "favorites" }
        if (favoritesCard != null) {
            recommended.add(favoritesCard)
            pool.remove(favoritesCard)
        }
        
        while (recommended.size < 4 && pool.isNotEmpty()) {
            val selectIdx = rand.nextInt(pool.size)
            recommended.add(pool.removeAt(selectIdx))
        }
        recommended
    }

    // Helpers to track focal item and adjust scroll position during pinch-to-zoom reflowing
    val findFocalMediaItem = remember(filteredMediaList, layoutMode, bentoRows, squareRows) {
        {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                null
            } else {
                val viewportMiddle = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val middleRow = visibleItems.minByOrNull { itemInfo: androidx.compose.foundation.lazy.LazyListItemInfo -> 
                    abs((itemInfo.offset + itemInfo.size / 2) - viewportMiddle) 
                }
                if (middleRow != null) {
                    val flatIndex = middleRow.index
                    var mediaItem: MediaItem? = null
                    var currentIndex = 1 // Skip carousels header at index 0
                    
                    outer@ for ((_, rows) in (if (layoutMode == "bento") bentoRows else squareRows)) {
                        if (flatIndex == currentIndex) {
                            mediaItem = rows.firstOrNull()?.let { spec ->
                                if (spec is BentoRowSpec.DenseRow) spec.items.firstOrNull()?.first else null
                            } ?: (rows.firstOrNull() as? List<*>)?.firstOrNull() as? MediaItem
                            break@outer
                        }
                        currentIndex += 1
                        
                        for (spec in rows) {
                            if (flatIndex == currentIndex) {
                                mediaItem = if (spec is BentoRowSpec.DenseRow) {
                                    spec.items.firstOrNull()?.first
                                } else {
                                    (spec as? List<*>)?.firstOrNull() as? MediaItem
                                }
                                break@outer
                            }
                            currentIndex += 1
                        }
                    }
                    mediaItem ?: filteredMediaList.firstOrNull()
                } else {
                    null
                }
            }
        }
    }

    val getLazyListIndexForMediaId = remember(groupedMedia, layoutMode) {
        { mediaId: Long, cols: Int ->
            var currentIndex = 1 // Skip carousels header
            var foundIndex: Int? = null
            
            if (layoutMode == "bento") {
                val targetBentoRows = groupedMedia.mapValues { (_, items) ->
                    packItemsIntoBentoRows(items, cols)
                }
                outer@ for ((_, rows) in targetBentoRows) {
                    currentIndex += 1
                    for (rowIndex in rows.indices) {
                        val spec = rows[rowIndex]
                        if (spec is BentoRowSpec.DenseRow) {
                            if (spec.items.any { it.first.id == mediaId }) {
                                foundIndex = currentIndex
                                break@outer
                            }
                        }
                        currentIndex += 1
                    }
                }
            } else if (layoutMode == "square") {
                val targetSquareRows = groupedMedia.mapValues { (_, items) ->
                    items.chunked(cols)
                }
                outer@ for ((_, rows) in targetSquareRows) {
                    currentIndex += 1
                    for (rowIndex in rows.indices) {
                        val rowItems = rows[rowIndex]
                        if (rowItems.any { it.id == mediaId }) {
                            foundIndex = currentIndex
                            break@outer
                        }
                        currentIndex += 1
                    }
                }
            }
            foundIndex
        }
    }

    // Pinch-to-Zoom grid column gesture tracker with buttery continuous scale and spring snap
    val gridScaleAnim = remember { Animatable(1f) }
    var isPinching by remember { mutableStateOf(false) }
    var lastColumnsChangeTime by remember { mutableStateOf(0L) }

    val pinchModifier = Modifier.pointerInput(gridColumns, layoutMode) {
        if (layoutMode != "justified" && layoutMode != "compact") {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPinching = false
                do {
                    val event = awaitPointerEvent()
                    val canceled = event.changes.any { it.isConsumed }
                    if (!canceled && event.changes.size >= 2) {
                        isPinching = true
                        val zoom = event.calculateZoom()
                        coroutineScope.launch {
                            gridScaleAnim.snapTo((gridScaleAnim.value * zoom).coerceIn(0.5f, 2.0f))
                        }
                        
                        val now = System.currentTimeMillis()
                        if (now - lastColumnsChangeTime > 450L) {
                            val currentScale = gridScaleAnim.value
                            var targetColumns = gridColumns
                            if (currentScale > 1.25f && gridColumns > 1) {
                                targetColumns = gridColumns - 1
                            } else if (currentScale < 0.75f && gridColumns < 6) {
                                targetColumns = gridColumns + 1
                            }
                            
                            if (targetColumns != gridColumns) {
                                lastColumnsChangeTime = now
                                val scaleCompensation = targetColumns.toFloat() / gridColumns.toFloat()
                                val focalItem = findFocalMediaItem()
                                
                                onColumnsChange(targetColumns)
                                
                                coroutineScope.launch {
                                    gridScaleAnim.snapTo((gridScaleAnim.value * scaleCompensation).coerceIn(0.5f, 2.0f))
                                    if (focalItem != null) {
                                        val targetIndex = getLazyListIndexForMediaId(focalItem.id, targetColumns)
                                        if (targetIndex != null) {
                                            lazyListState.scrollToItem(targetIndex, 0)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } while (event.changes.any { it.pressed })

                if (isPinching) {
                    isPinching = false
                    coroutineScope.launch {
                        gridScaleAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                stiffness = Spring.StiffnessMediumLow,
                                dampingRatio = Spring.DampingRatioLowBouncy
                            )
                        )
                    }
                }
            }
        }
    }

    // Selection toggle helper
    val toggleSelection: (MediaItem) -> Unit = { item ->
        if (selectedItems.any { it.id == item.id }) {
            selectedItems.removeAll { it.id == item.id }
            if (selectedItems.isEmpty()) {
                isSelectionMode = false
            }
        } else {
            selectedItems.add(item)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val gridBlurAlpha by animateDpAsState(
            targetValue = if (previewItem != null) 12.dp else 0.dp,
            animationSpec = tween(durationMillis = 200)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(gridBlurAlpha)
        ) {
            // Selection mode header
            if (isSelectionMode) {
                Surface(
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShadcnButton(
                                onClick = {
                                    isSelectionMode = false
                                    selectedItems.clear()
                                },
                                variant = ShadcnButtonVariant.Ghost
                            ) {
                                Text("Cancel", fontWeight = FontWeight.SemiBold)
                            }
                            Text(
                                text = "${selectedItems.size} Selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = onShareSelected) {
                                Icon(Icons.Default.Share, contentDescription = "Share", tint = MaterialTheme.colorScheme.onBackground)
                            }
                            IconButton(onClick = onDeleteSelected) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (filteredMediaList.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No media files matching criteria",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                        Spacer(Modifier.height(8.dp))
                        ShadcnButton(onClick = { selectedCategory = "All" }, variant = ShadcnButtonVariant.Outline) {
                            Text("Reset Category Filters")
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .then(pinchModifier)
                        .graphicsLayer {
                            scaleX = gridScaleAnim.value
                            scaleY = gridScaleAnim.value
                        },
                    verticalArrangement = Arrangement.spacedBy(
                        if (layoutMode == "compact") 4.dp
                        else if (layoutMode == "bento" && gridColumns >= 5) 4.dp
                        else if (layoutMode == "bento") 8.dp
                        else 16.dp
                    ),
                    contentPadding = PaddingValues(top = topPadding, bottom = 80.dp)
                ) {
                    // --- CAROUSELS HEADER SECTION ---
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Category Row Quick Selectors
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(categories) { category ->
                                    val isCatSelected = selectedCategory == category
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(if (isCatSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                                            .clickable { selectedCategory = category }
                                            .padding(horizontal = 16.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = category,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                            color = if (isCatSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // Dynamic Memory Spotlight Cards Carousel
                            if (memoryItems.isNotEmpty() && selectedCategory == "All") {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Spotlight Memories",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                                    )
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(memoryItems) { memory ->
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 160.dp, height = 100.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        if (memory.items.isNotEmpty()) {
                                                            onMediaClick(memory.items, 0, null)
                                                        }
                                                    }
                                            ) {
                                                AsyncImage(
                                                    model = memory.coverUri,
                                                    contentDescription = memory.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .background(
                                                            Brush.verticalGradient(
                                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                                            )
                                                        )
                                                )
                                                Column(
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(10.dp)
                                                ) {
                                                    Text(
                                                        text = memory.title,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        text = memory.subtitle,
                                                        color = Color.White.copy(alpha = 0.7f),
                                                        fontSize = 10.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // --- PHOTOS SECTION & GRID RENDERING ---
                    if (layoutMode == "bento") {
                        bentoRows.forEach { (dateHeader, rows) ->
                            stickyHeader { DateHeader(dateHeader) }
                            items(rows) { spec ->
                                val bentoSpacing = if (gridColumns >= 5) 4.dp else 8.dp
                                when (spec) {
                                    is BentoRowSpec.DenseRow -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(bentoSpacing)
                                        ) {
                                            spec.items.forEach { (item, span) ->
                                                BentoImageTile(
                                                    item = item,
                                                    modifier = Modifier
                                                        .weight(span.toFloat())
                                                        .aspectRatio(span * 0.75f),
                                                    isSelected = selectedItems.any { it.id == item.id },
                                                    isSelectionMode = isSelectionMode,
                                                    onSelectToggle = {
                                                        if (!isSelectionMode) isSelectionMode = true
                                                        toggleSelection(item)
                                                    },
                                                    onClick = { bounds ->
                                                         if (isSelectionMode) {
                                                             toggleSelection(item)
                                                         } else {
                                                             onMediaClick(filteredMediaList, filteredMediaList.indexOf(item), bounds)
                                                         }
                                                    },
                                                    onPreviewTrigger = { previewItem = it }
                                                )
                                            }
                                            val totalSpan = spec.items.sumOf { it.second }
                                            if (totalSpan < gridColumns) {
                                                Spacer(modifier = Modifier.weight((gridColumns - totalSpan).toFloat()))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    else if (layoutMode == "square") {
                        squareRows.forEach { (dateHeader, rows) ->
                            stickyHeader { DateHeader(dateHeader) }
                            items(rows) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        BentoImageTile(
                                            item = item,
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            isSelected = selectedItems.any { it.id == item.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(item)
                                            },
                                            onClick = { bounds ->
                                                if (isSelectionMode) {
                                                    toggleSelection(item)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(item), bounds)
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                    }
                                    val emptySlots = gridColumns - rowItems.size
                                    if (emptySlots > 0) {
                                        repeat(emptySlots) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else if (layoutMode == "compact") {
                        compactRows.forEach { (dateHeader, rows) ->
                            stickyHeader { DateHeader(dateHeader) }
                            items(rows) { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        BentoImageTile(
                                            item = item,
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            isSelected = selectedItems.any { it.id == item.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(item)
                                            },
                                            onClick = { bounds ->
                                                if (isSelectionMode) {
                                                    toggleSelection(item)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(item), bounds)
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                    }
                                    val emptySlots = 5 - rowItems.size
                                    if (emptySlots > 0) {
                                        repeat(emptySlots) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PREMIUM FLOATING QUICK PREVIEW OVERLAY (No Blur, Buttery Spring Animation) ---
        AnimatedVisibility(
            visible = previewItem != null,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { previewItem = null }, // Tap overlay to dismiss
                contentAlignment = Alignment.Center
            ) {
                previewItem?.let { item ->
                    var animScale by remember { mutableStateOf(0.85f) }
                    LaunchedEffect(item) {
                        animScale = 1.0f
                    }
                    val scale by animateFloatAsState(
                        targetValue = animScale,
                        animationSpec = spring(
                            stiffness = Spring.StiffnessMediumLow,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    )

                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .wrapContentHeight()
                            .clickable(enabled = false) {} // Prevent click-through
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                        shape = RoundedCornerShape(20.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                            ) {
                                AsyncImage(
                                    model = item.uri,
                                    contentDescription = "Preview Image",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (item.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(99.dp))
                                                .background(Color.Black.copy(alpha = 0.5f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Play Video Preview",
                                                tint = Color.White,
                                                modifier = Modifier.size(32.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = SimpleDateFormat("MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date(item.dateAdded * 1000)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Memory Spotlight Card spec class
private data class MemoryCardInfo(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUri: Uri,
    val items: List<MediaItem>
)

// Reusable Date Header Composable
@Composable
private fun DateHeader(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// Bento Image Tile Renderer with Smart Content-Aware Cropping Heuristics
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoImageTile(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectToggle: () -> Unit,
    onClick: (RectBounds?) -> Unit,
    onPreviewTrigger: (MediaItem?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var currentLayoutCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Heuristics-based focal-point cropping to prevent cutting off heads/text
    val smartAlignment = remember(item) {
        when {
            // Keep top visible for screenshots/docs to ensure text headings are readable
            item.bucketName.contains("Screenshot", ignoreCase = true) ||
            item.name.contains("Screenshot", ignoreCase = true) ||
            item.mimeType.contains("document", ignoreCase = true) -> Alignment.TopCenter
            
            // Shift crop box slightly up for portrait photos/selfies to preserve faces/eyes/hairlines
            item.width > 0 && item.height > 0 && item.width < item.height -> BiasAlignment(0f, -0.35f)
            
            else -> Alignment.Center
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { currentLayoutCoordinates = it }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondary)
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) BlueAccent else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .pointerInput(item, isSelectionMode) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown()
                        var longPressed = false
                        val longPressJob = coroutineScope.launch {
                            delay(400) // 400ms hold triggers floating quick preview
                            longPressed = true
                            if (!isSelectionMode) {
                                onPreviewTrigger(item)
                            }
                        }

                        val up = waitForUpOrCancellation()
                        longPressJob.cancel()
                        if (longPressed) {
                            onPreviewTrigger(null) // Dismiss preview on release
                        } else if (up != null) {
                            if (isSelectionMode) {
                                onSelectToggle()
                            } else {
                                val coordinates = currentLayoutCoordinates
                                val bounds = if (coordinates != null && coordinates.isAttached) {
                                    val pos = coordinates.positionInWindow()
                                    val size = coordinates.size
                                    RectBounds(pos.x, pos.y, size.width.toFloat(), size.height.toFloat())
                                } else {
                                    null
                                }
                                onClick(bounds)
                            }
                        }
                    }
                }
            }
    ) {
        val context = LocalContext.current
        val imageModel = remember(item) {
            if (item.isVideo) {
                ImageRequest.Builder(context)
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
            contentScale = ContentScale.Crop,
            alignment = smartAlignment
        )

        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
            val durationString = remember(item.duration) {
                val totalSeconds = item.duration / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
            ShadcnBadge(
                text = durationString,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                color = Color.Black.copy(alpha = 0.6f),
                textColor = Color.White
            )
        }
    }
}

// Dynamic Bento Grid Packing Algorithm with Editorial Spacing Rules
private fun packItemsIntoBentoRows(items: List<MediaItem>, columns: Int): List<BentoRowSpec> {
    val specs = mutableListOf<BentoRowSpec>()
    var i = 0
    val n = items.size
    var alternateLeft = false

    while (i < n) {
        val rowItems = mutableListOf<Pair<MediaItem, Int>>()
        var currentSpanSum = 0
        
        // Accumulate items for the current row
        val rowCandidates = mutableListOf<MediaItem>()
        while (i < n && currentSpanSum < columns) {
            val item = items[i]
            val aspect = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else 1f
            
            // Highlight favorites as span 2 if columns count allows
            val idealSpan = when {
                item.isFavorite && columns >= 3 -> 2
                aspect >= 1.75f && columns >= 3 -> 3
                aspect >= 1.2f && columns >= 2 -> 2
                else -> 1
            }
            
            val clampedIdeal = idealSpan.coerceAtMost(columns)
            rowCandidates.add(item)
            currentSpanSum += clampedIdeal
            i++
        }
        
        // Now adjust the spans of candidates to fit the row capacity exactly
        if (currentSpanSum == columns) {
            rowCandidates.forEach { item ->
                val aspect = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else 1f
                val idealSpan = when {
                    item.isFavorite && columns >= 3 -> 2
                    aspect >= 1.75f && columns >= 3 -> 3
                    aspect >= 1.2f && columns >= 2 -> 2
                    else -> 1
                }.coerceAtMost(columns)
                rowItems.add(Pair(item, idealSpan))
            }
        } else if (currentSpanSum > columns) {
            val candidateSpans = rowCandidates.map { item ->
                val aspect = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else 1f
                val idealSpan = when {
                    item.isFavorite && columns >= 3 -> 2
                    aspect >= 1.75f && columns >= 3 -> 3
                    aspect >= 1.2f && columns >= 2 -> 2
                    else -> 1
                }.coerceAtMost(columns)
                idealSpan
            }.toMutableList()
            
            var excess = currentSpanSum - columns
            while (excess > 0) {
                var bestReduceIdx = -1
                var maxSpan = 1
                for (j in candidateSpans.indices) {
                    if (candidateSpans[j] > maxSpan) {
                        maxSpan = candidateSpans[j]
                        bestReduceIdx = j
                    }
                }
                
                if (bestReduceIdx != -1) {
                    candidateSpans[bestReduceIdx] = candidateSpans[bestReduceIdx] - 1
                    excess--
                } else {
                    break
                }
            }
            
            // Alternating pattern placement to distribute visual weight evenly
            val hasFeatureTile = candidateSpans.any { it >= 2 }
            if (hasFeatureTile && candidateSpans.size >= 2) {
                if (alternateLeft) {
                    rowCandidates.reverse()
                    candidateSpans.reverse()
                }
                alternateLeft = !alternateLeft
            }
            
            for (j in rowCandidates.indices) {
                rowItems.add(Pair(rowCandidates[j], candidateSpans[j]))
            }
        } else {
            // Leftovers: Keep their ideal spans to prevent aspect ratio distortion
            rowCandidates.forEach { item ->
                val aspect = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else 1f
                val idealSpan = when {
                    item.isFavorite && columns >= 3 -> 2
                    aspect >= 1.75f && columns >= 3 -> 3
                    aspect >= 1.2f && columns >= 2 -> 2
                    else -> 1
                }.coerceAtMost(columns)
                rowItems.add(Pair(item, idealSpan))
            }
        }
        
        specs.add(BentoRowSpec.DenseRow(rowItems))
    }
    return specs
}

// Helpers
private fun getHeaderDateString(timestampSeconds: Long): String {
    val date = Date(timestampSeconds * 1000)
    val today = Calendar.getInstance()
    val itemDate = Calendar.getInstance().apply { time = date }

    return when {
        isSameDay(today, itemDate) -> "Today"
        isYesterday(today, itemDate) -> "Yesterday"
        else -> SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
           cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isYesterday(today: Calendar, date: Calendar): Boolean {
    val yesterday = today.clone() as Calendar
    yesterday.add(Calendar.DAY_OF_YEAR, -1)
    return isSameDay(yesterday, date)
}
