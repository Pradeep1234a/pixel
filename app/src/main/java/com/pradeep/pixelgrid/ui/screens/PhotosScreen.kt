package com.pradeep.pixelgrid.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.pradeep.pixelgrid.data.MediaItem
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
    data class LargeLeft(val large: MediaItem, val topSmall: MediaItem, val bottomSmall: MediaItem) : BentoRowSpec()
    data class LargeRight(val topSmall: MediaItem, val bottomSmall: MediaItem, val large: MediaItem) : BentoRowSpec()
    data class ThreeSmall(val item1: MediaItem, val item2: MediaItem, val item3: MediaItem) : BentoRowSpec()
    data class TwoSmall(val item1: MediaItem, val item2: MediaItem) : BentoRowSpec()
    data class OneSmall(val item1: MediaItem) : BentoRowSpec()
    data class Panoramic(val item: MediaItem) : BentoRowSpec()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    mediaList: List<MediaItem>,
    gridColumns: Int,
    onColumnsChange: (Int) -> Unit,
    onMediaClick: (List<MediaItem>, Int) -> Unit,
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
    val groupedMediaRows = remember(groupedMedia, gridColumns) {
        groupedMedia.mapValues { (_, items) ->
            packItemsIntoBentoRows(items, gridColumns)
        }
    }

    // Dynamic memories generation
    val memoryItems = remember(mediaList) {
        val list = mutableListOf<MemoryCardInfo>()
        
        // Favorites memory card
        val favs = mediaList.filter { it.isFavorite }
        if (favs.isNotEmpty()) {
            list.add(MemoryCardInfo("Favorites", favs.first().uri, "Your absolute highlights"))
        }

        // On This Day memory card
        val calendarToday = Calendar.getInstance()
        val todayMonth = calendarToday.get(Calendar.MONTH)
        val todayDay = calendarToday.get(Calendar.DAY_OF_MONTH)
        val onThisDayPhotos = mediaList.filter {
            val photoCal = Calendar.getInstance().apply { time = Date(it.dateAdded * 1000) }
            photoCal.get(Calendar.MONTH) == todayMonth && photoCal.get(Calendar.DAY_OF_MONTH) == todayDay && photoCal.get(Calendar.YEAR) < calendarToday.get(Calendar.YEAR)
        }
        if (onThisDayPhotos.isNotEmpty()) {
            list.add(MemoryCardInfo("On This Day", onThisDayPhotos.first().uri, "Flashbacks from years past"))
        }

        // Recent videos card
        val videos = mediaList.filter { it.isVideo }
        if (videos.isNotEmpty()) {
            list.add(MemoryCardInfo("Recent Clips", videos.first().uri, "Relive action captures"))
        }

        // Recently Added card
        if (mediaList.isNotEmpty()) {
            list.add(MemoryCardInfo("Recent Shots", mediaList.first().uri, "Fresh in your vault"))
        }

        list
    }

    // Pinch-to-Zoom grid column gesture tracker
    var zoomAccumulator by remember { mutableStateOf(1f) }
    val pinchModifier = Modifier.pointerInput(gridColumns) {
        detectTransformGestures { _, _, zoom, _ ->
            zoomAccumulator *= zoom
            if (zoomAccumulator > 1.35f) {
                if (gridColumns > 1) {
                    onColumnsChange(gridColumns - 1)
                }
                zoomAccumulator = 1f
            } else if (zoomAccumulator < 0.65f) {
                if (gridColumns < 6) {
                    onColumnsChange(gridColumns + 1)
                }
                zoomAccumulator = 1f
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
        Column(modifier = Modifier.fillMaxSize()) {
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
                        .then(pinchModifier),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                                        val targetIndex = mediaList.indexOfFirst {
                                                            when (memory.title) {
                                                                "Favorites" -> it.isFavorite
                                                                "Recent Clips" -> it.isVideo
                                                                else -> true
                                                            }
                                                        }
                                                        if (targetIndex != -1) {
                                                            onMediaClick(mediaList, targetIndex)
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

                    // --- BENTO GRID SECTIONS ---
                    groupedMediaRows.forEach { (dateHeader, rows) ->
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(
                                    text = dateHeader,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }

                        items(rows) { spec ->
                            when (spec) {
                                is BentoRowSpec.Panoramic -> {
                                    BentoImageTile(
                                        item = spec.item,
                                        modifier = Modifier.fillMaxWidth().aspectRatio(2.2f),
                                        isSelected = selectedItems.any { it.id == spec.item.id },
                                        isSelectionMode = isSelectionMode,
                                        onSelectToggle = {
                                            if (!isSelectionMode) isSelectionMode = true
                                            toggleSelection(spec.item)
                                        },
                                        onClick = {
                                            if (isSelectionMode) {
                                                toggleSelection(spec.item)
                                            } else {
                                                onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.item))
                                            }
                                        },
                                        onPreviewTrigger = { previewItem = it }
                                    )
                                }
                                is BentoRowSpec.OneSmall -> {
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        BentoImageTile(
                                            item = spec.item1,
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            isSelected = selectedItems.any { it.id == spec.item1.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(spec.item1)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(spec.item1)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.item1))
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                        if (gridColumns >= 2) {
                                            val spaceWeight = gridColumns - 1
                                            Spacer(modifier = Modifier.weight(spaceWeight.toFloat()))
                                        }
                                    }
                                }
                                is BentoRowSpec.TwoSmall -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BentoImageTile(
                                            item = spec.item1,
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            isSelected = selectedItems.any { it.id == spec.item1.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(spec.item1)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(spec.item1)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.item1))
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                        BentoImageTile(
                                            item = spec.item2,
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            isSelected = selectedItems.any { it.id == spec.item2.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(spec.item2)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(spec.item2)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.item2))
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                        if (gridColumns >= 3) {
                                            val spaceWeight = gridColumns - 2
                                            Spacer(modifier = Modifier.weight(spaceWeight.toFloat()))
                                        }
                                    }
                                }
                                is BentoRowSpec.ThreeSmall -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BentoImageTile(
                                            item = spec.item1,
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            isSelected = selectedItems.any { it.id == spec.item1.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(spec.item1)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(spec.item1)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.item1))
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                        BentoImageTile(
                                            item = spec.item2,
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            isSelected = selectedItems.any { it.id == spec.item2.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(spec.item2)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(spec.item2)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.item2))
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                        BentoImageTile(
                                            item = spec.item3,
                                            modifier = Modifier.weight(1f).aspectRatio(1f),
                                            isSelected = selectedItems.any { it.id == spec.item3.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(spec.item3)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(spec.item3)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.item3))
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                        if (gridColumns >= 4) {
                                            val spaceWeight = gridColumns - 3
                                            Spacer(modifier = Modifier.weight(spaceWeight.toFloat()))
                                        }
                                    }
                                }
                                is BentoRowSpec.LargeLeft -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        BentoImageTile(
                                            item = spec.large,
                                            modifier = Modifier.weight(2f).aspectRatio(0.96f),
                                            isSelected = selectedItems.any { it.id == spec.large.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(spec.large)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(spec.large)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.large))
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            BentoImageTile(
                                                item = spec.topSmall,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                                isSelected = selectedItems.any { it.id == spec.topSmall.id },
                                                isSelectionMode = isSelectionMode,
                                                onSelectToggle = {
                                                    if (!isSelectionMode) isSelectionMode = true
                                                    toggleSelection(spec.topSmall)
                                                },
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        toggleSelection(spec.topSmall)
                                                    } else {
                                                        onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.topSmall))
                                                    }
                                                },
                                                onPreviewTrigger = { previewItem = it }
                                            )
                                            BentoImageTile(
                                                item = spec.bottomSmall,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                                isSelected = selectedItems.any { it.id == spec.bottomSmall.id },
                                                isSelectionMode = isSelectionMode,
                                                onSelectToggle = {
                                                    if (!isSelectionMode) isSelectionMode = true
                                                    toggleSelection(spec.bottomSmall)
                                                },
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        toggleSelection(spec.bottomSmall)
                                                    } else {
                                                        onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.bottomSmall))
                                                    }
                                                },
                                                onPreviewTrigger = { previewItem = it }
                                            )
                                        }
                                        if (gridColumns >= 4) {
                                            val spaceWeight = gridColumns - 3
                                            Spacer(modifier = Modifier.weight(spaceWeight.toFloat()))
                                        }
                                    }
                                }
                                is BentoRowSpec.LargeRight -> {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            BentoImageTile(
                                                item = spec.topSmall,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                                isSelected = selectedItems.any { it.id == spec.topSmall.id },
                                                isSelectionMode = isSelectionMode,
                                                onSelectToggle = {
                                                    if (!isSelectionMode) isSelectionMode = true
                                                    toggleSelection(spec.topSmall)
                                                },
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        toggleSelection(spec.topSmall)
                                                    } else {
                                                        onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.topSmall))
                                                    }
                                                },
                                                onPreviewTrigger = { previewItem = it }
                                            )
                                            BentoImageTile(
                                                item = spec.bottomSmall,
                                                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                                                isSelected = selectedItems.any { it.id == spec.bottomSmall.id },
                                                isSelectionMode = isSelectionMode,
                                                onSelectToggle = {
                                                    if (!isSelectionMode) isSelectionMode = true
                                                    toggleSelection(spec.bottomSmall)
                                                },
                                                onClick = {
                                                    if (isSelectionMode) {
                                                        toggleSelection(spec.bottomSmall)
                                                    } else {
                                                        onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.bottomSmall))
                                                    }
                                                },
                                                onPreviewTrigger = { previewItem = it }
                                            )
                                        }
                                        BentoImageTile(
                                            item = spec.large,
                                            modifier = Modifier.weight(2f).aspectRatio(0.96f),
                                            isSelected = selectedItems.any { it.id == spec.large.id },
                                            isSelectionMode = isSelectionMode,
                                            onSelectToggle = {
                                                if (!isSelectionMode) isSelectionMode = true
                                                toggleSelection(spec.large)
                                            },
                                            onClick = {
                                                if (isSelectionMode) {
                                                    toggleSelection(spec.large)
                                                } else {
                                                    onMediaClick(filteredMediaList, filteredMediaList.indexOf(spec.large))
                                                }
                                            },
                                            onPreviewTrigger = { previewItem = it }
                                        )
                                        if (gridColumns >= 4) {
                                            val spaceWeight = gridColumns - 3
                                            Spacer(modifier = Modifier.weight(spaceWeight.toFloat()))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- PREMIUM BLURRED FLOATING QUICK PREVIEW OVERLAY ---
        previewItem?.let { item ->
            Dialog(
                onDismissRequest = { previewItem = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    dismissOnBackPress = true,
                    dismissOnClickOutside = true
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .blur(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .wrapContentHeight()
                            .padding(16.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
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
                                                .size(60.dp)
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

// Bento Image Tile Renderer
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BentoImageTile(
    item: MediaItem,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onSelectToggle: () -> Unit,
    onClick: () -> Unit,
    onPreviewTrigger: (MediaItem?) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    Box(
        modifier = modifier
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
                                onClick()
                            }
                        }
                    }
                }
            }
    ) {
        AsyncImage(
            model = item.uri,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
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

// Memory Spotlight Card spec class
private data class MemoryCardInfo(
    val title: String,
    val coverUri: Uri,
    val subtitle: String
)

// Dynamic Bento Grid Packing Algorithm
private fun packItemsIntoBentoRows(items: List<MediaItem>, columns: Int): List<BentoRowSpec> {
    val specs = mutableListOf<BentoRowSpec>()
    var i = 0
    val n = items.size

    while (i < n) {
        if (columns < 3) {
            if (columns == 1) {
                specs.add(BentoRowSpec.OneSmall(items[i]))
                i += 1
            } else { // columns == 2
                if (i + 1 < n) {
                    specs.add(BentoRowSpec.TwoSmall(items[i], items[i + 1]))
                    i += 2
                } else {
                    specs.add(BentoRowSpec.OneSmall(items[i]))
                    i += 1
                }
            }
            continue
        }

        // columns >= 3
        val current = items[i]

        // 1. Panoramic landscape featured photos
        if (current.isFavorite || (current.width > current.height * 1.5f && !current.isVideo)) {
            specs.add(BentoRowSpec.Panoramic(current))
            i += 1
        }
        // 2. LargeLeft Template
        else if (i + 2 < n && i % 5 == 0) {
            specs.add(BentoRowSpec.LargeLeft(items[i], items[i + 1], items[i + 2]))
            i += 3
        }
        // 3. LargeRight Template
        else if (i + 2 < n && i % 5 == 2) {
            specs.add(BentoRowSpec.LargeRight(items[i], items[i + 1], items[i + 2]))
            i += 3
        }
        // 4. ThreeSmall Template
        else if (i + 2 < n) {
            specs.add(BentoRowSpec.ThreeSmall(items[i], items[i + 1], items[i + 2]))
            i += 3
        }
        // 5. TwoSmall Template
        else if (i + 1 < n) {
            specs.add(BentoRowSpec.TwoSmall(items[i], items[i + 1]))
            i += 2
        }
        // 6. OneSmall Template
        else {
            specs.add(BentoRowSpec.OneSmall(items[i]))
            i += 1
        }
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
