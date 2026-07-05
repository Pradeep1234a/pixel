package com.pradeep.pixelgrid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.pradeep.pixelgrid.data.MediaBucket
import com.pradeep.pixelgrid.data.MediaItem
import com.pradeep.pixelgrid.ui.components.ShadcnCard
import com.pradeep.pixelgrid.ui.components.ShadcnBadge
import androidx.compose.ui.graphics.Color
import java.util.Locale

@Composable
fun AlbumsScreen(
    mediaList: List<MediaItem>,
    gridColumns: Int,
    onMediaClick: (List<MediaItem>, Int) -> Unit,
    topPadding: Dp,
    onScrollChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var activeBucketName by remember { mutableStateOf<String?>(null) }

    // Group items into buckets
    val buckets = remember(mediaList) {
        mediaList.groupBy { it.bucketName }
            .map { (name, items) ->
                MediaBucket(
                    name = name,
                    coverUri = items.first().uri,
                    itemCount = items.size
                )
            }.sortedByDescending { it.itemCount }
    }

    // Scroll states for collapsing app bar
    val foldersListState = rememberLazyGridState()
    val detailListState = rememberLazyGridState()

    val activeState = if (activeBucketName == null) foldersListState else detailListState
    val scrollFraction by remember {
        derivedStateOf {
            if (activeState.firstVisibleItemIndex == 0) {
                (activeState.firstVisibleItemScrollOffset.toFloat() / 200f).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }

    LaunchedEffect(scrollFraction) {
        if (activeBucketName == null) {
            onScrollChange(scrollFraction)
        }
    }

    LaunchedEffect(activeBucketName) {
        onScrollChange(0f)
    }

    // Intercept back handler when viewing album detail
    if (activeBucketName != null) {
        BackHandler {
            activeBucketName = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (activeBucketName == null) {
            // 1. FOLDERS/ALBUMS GRID (No local title, collapsing top padding applied)
            if (buckets.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No albums found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = foldersListState,
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = topPadding, bottom = 80.dp)
                ) {
                    items(buckets) { bucket ->
                        AlbumCard(
                            bucket = bucket,
                            onClick = { activeBucketName = bucket.name }
                        )
                    }
                }
            }
        } else {
            // 2. ALBUM DETAIL SCREEN (Standard compact back-button header)
            val albumName = activeBucketName!!
            val albumMedia = remember(mediaList, albumName) {
                mediaList.filter { it.bucketName == albumName }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeBucketName = null }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back to Albums",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${albumMedia.size} items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                }

                LazyVerticalGrid(
                    state = detailListState,
                    columns = GridCells.Fixed(gridColumns),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(albumMedia) { item ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondary)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .clickable { onMediaClick(albumMedia, albumMedia.indexOf(item)) }
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
                }
            }
        }
    }
}

// Album Card view component
@Composable
private fun AlbumCard(
    bucket: MediaBucket,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ShadcnCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.secondary)
            ) {
                val context = LocalContext.current
                val coverModel = remember(bucket.coverUri) {
                    ImageRequest.Builder(context)
                        .data(bucket.coverUri)
                        .videoFrameMillis(1000)
                        .build()
                }
                AsyncImage(
                    model = coverModel,
                    contentDescription = bucket.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = bucket.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${bucket.itemCount} items",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}
