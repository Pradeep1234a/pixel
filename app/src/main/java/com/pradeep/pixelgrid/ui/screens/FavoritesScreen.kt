package com.pradeep.pixelgrid.ui.screens

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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import androidx.compose.ui.platform.LocalContext
import com.pradeep.pixelgrid.data.MediaItem
import com.pradeep.pixelgrid.ui.components.ShadcnBadge
import java.util.Locale

@Composable
fun FavoritesScreen(
    mediaList: List<MediaItem>,
    gridColumns: Int,
    onMediaClick: (List<MediaItem>, Int) -> Unit,
    topPadding: Dp,
    onScrollChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val favorites = remember(mediaList) {
        mediaList.filter { it.isFavorite }
    }

    val gridState = rememberLazyGridState()
    val scrollFraction by remember {
        derivedStateOf {
            if (gridState.firstVisibleItemIndex == 0) {
                (gridState.firstVisibleItemScrollOffset.toFloat() / 200f).coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }

    LaunchedEffect(scrollFraction) {
        onScrollChange(scrollFraction)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "No Favorites",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = "No favorite items yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Tap the heart icon while viewing a photo to add it here",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(gridColumns),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(top = topPadding, bottom = 80.dp)
            ) {
                items(favorites) { item ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.secondary)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                            .clickable { onMediaClick(favorites, favorites.indexOf(item)) }
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
