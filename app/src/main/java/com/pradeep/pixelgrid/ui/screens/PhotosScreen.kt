package com.pradeep.pixelgrid.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.pradeep.pixelgrid.data.MediaItem
import com.pradeep.pixelgrid.data.MediaRepository
import com.pradeep.pixelgrid.ui.components.ShadcnBadge
import com.pradeep.pixelgrid.ui.components.ShadcnButton
import com.pradeep.pixelgrid.ui.components.ShadcnButtonVariant
import com.pradeep.pixelgrid.ui.theme.BlueAccent
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotosScreen(
    mediaList: List<MediaItem>,
    gridColumns: Int,
    onMediaClick: (MediaItem) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Selection mode state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<MediaItem>() }

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

    // Reset selection mode if mediaList changes or screen is refreshed
    LaunchedEffect(mediaList) {
        if (selectedItems.isNotEmpty()) {
            // Keep selected items that still exist in the updated media list
            val currentIds = mediaList.map { it.id }.toSet()
            val toRemove = selectedItems.filter { !currentIds.contains(it.id) }
            selectedItems.removeAll(toRemove)
            if (selectedItems.isEmpty()) {
                isSelectionMode = false
            }
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
                        break // Handle one exception at a time (standard Android Q+ flow)
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

    // Group media by date
    val groupedMedia = remember(mediaList) {
        mediaList.groupBy { getHeaderDateString(it.dateAdded) }
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Selection Mode Header or standard title
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
        } else {
            // Normal top header
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Photos",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${mediaList.size} items in total",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                
                ShadcnBadge(text = "Zinc Style", color = MaterialTheme.colorScheme.primary, textColor = MaterialTheme.colorScheme.onPrimary)
            }
        }

        if (mediaList.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No photos or videos found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Try taking some photos or verify storage permissions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(8.dp))
                    ShadcnButton(onClick = onRefresh, variant = ShadcnButtonVariant.Outline) {
                        Text("Refresh Gallery")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                groupedMedia.forEach { (dateHeader, items) ->
                    // Sticky Date Header
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

                    // Grid Row layout
                    val chunks = items.chunked(gridColumns)
                    items(chunks) { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { item ->
                                val isSelected = selectedItems.any { it.id == item.id }
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.secondary)
                                        .border(
                                            width = if (isSelected) 3.dp else 1.dp,
                                            color = if (isSelected) BlueAccent else MaterialTheme.colorScheme.outline,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .combinedClickable(
                                            onClick = {
                                                if (isSelectionMode) {
                                                    if (isSelected) {
                                                        selectedItems.removeAll { it.id == item.id }
                                                        if (selectedItems.isEmpty()) {
                                                            isSelectionMode = false
                                                        }
                                                    } else {
                                                        selectedItems.add(item)
                                                    }
                                                } else {
                                                    onMediaClick(item)
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    isSelectionMode = true
                                                    selectedItems.add(item)
                                                }
                                            }
                                        )
                                ) {
                                    // Thumbnail Image
                                    AsyncImage(
                                        model = item.uri,
                                        contentDescription = item.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )

                                    // Selection checkbox overlay
                                    if (isSelectionMode) {
                                        Box(
                                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = if (isSelected) 0.3f else 0.1f))
                                        )
                                        
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = if (isSelected) BlueAccent else Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                                        )
                                    }

                                    // Video Play Overlay and duration
                                    if (item.isVideo) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color.Black.copy(alpha = 0.6f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Default.PlayArrow,
                                                    contentDescription = "Video",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = formatDuration(item.duration),
                                                    color = Color.White,
                                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp)
                                                )
                                            }
                                        }
                                    }

                                    // Favorite badge indicator
                                    if (item.isFavorite && !isSelectionMode) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = "Favorite",
                                            tint = Color.Red,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .size(14.dp)
                                        )
                                    }
                                }
                            }

                            // Pad row if it has fewer elements than columns
                            val emptySlots = gridColumns - rowItems.size
                            if (emptySlots > 0) {
                                repeat(emptySlots) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

// Format duration from ms to m:ss
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
}

// Convert timestamp to readable date header
private fun getHeaderDateString(timestampSeconds: Long): String {
    val date = Date(timestampSeconds * 1000)
    val now = Calendar.getInstance()
    val itemCalendar = Calendar.getInstance().apply { time = date }

    return when {
        // Today
        now.get(Calendar.YEAR) == itemCalendar.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == itemCalendar.get(Calendar.DAY_OF_YEAR) -> "Today"

        // Yesterday
        now.get(Calendar.YEAR) == itemCalendar.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - 1 == itemCalendar.get(Calendar.DAY_OF_YEAR) -> "Yesterday"

        // This Year, e.g. "June 14"
        now.get(Calendar.YEAR) == itemCalendar.get(Calendar.YEAR) -> {
            SimpleDateFormat("MMMM d", Locale.getDefault()).format(date)
        }

        // Previous Years, e.g. "October 10, 2024"
        else -> {
            SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()).format(date)
        }
    }
}
