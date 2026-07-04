package com.pradeep.pixelgrid.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pradeep.pixelgrid.ui.components.ShadcnButton
import com.pradeep.pixelgrid.ui.components.ShadcnButtonVariant
import com.pradeep.pixelgrid.ui.components.ShadcnCard
import com.pradeep.pixelgrid.ui.components.ShadcnTabSwitch
import coil.annotation.ExperimentalCoilApi
import java.util.*

@OptIn(ExperimentalCoilApi::class)
@Composable
fun SettingsScreen(
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    gridColumns: Int,
    onColumnsChange: (Int) -> Unit,
    videoAutoplay: Boolean,
    onVideoAutoplayChange: (Boolean) -> Unit,
    updateChannel: String,
    onUpdateChannelChange: (String) -> Unit,
    totalCount: Int,
    totalSize: Long,
    onCheckForUpdates: () -> Unit,
    topPadding: Dp,
    onScrollChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current

    val scrollFraction by remember {
        derivedStateOf {
            (scrollState.value.toFloat() / 200f).coerceIn(0f, 1f)
        }
    }

    LaunchedEffect(scrollFraction) {
        onScrollChange(scrollFraction)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(top = topPadding, bottom = 80.dp)
            .padding(horizontal = 16.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // --- THEME SELECTOR CARD ---
            ShadcnCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "App Theme",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Select your preferred visual style",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                
                ShadcnTabSwitch(
                    options = listOf("Light", "Dark"),
                    selectedIndex = if (darkTheme) 1 else 0,
                    onOptionSelected = { index -> onThemeChange(index == 1) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- GRID LAYOUT CARD ---
            ShadcnCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Grid Layout Columns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Customize photo stream grid columns density",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                ShadcnTabSwitch(
                    options = listOf("2 Columns", "3 Columns", "4 Columns"),
                    selectedIndex = when (gridColumns) {
                        2 -> 0
                        3 -> 1
                        else -> 2
                    },
                    onOptionSelected = { index ->
                        val cols = when (index) {
                            0 -> 2
                            1 -> 3
                            else -> 4
                        }
                        onColumnsChange(cols)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- VIDEO PREFERENCES CARD ---
            ShadcnCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Video Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Configure autoplay rules inside viewer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                ShadcnTabSwitch(
                    options = listOf("Autoplay On", "Autoplay Off"),
                    selectedIndex = if (videoAutoplay) 0 else 1,
                    onOptionSelected = { index -> onVideoAutoplayChange(index == 0) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- CLEAR STORAGE CACHE ---
            ShadcnCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Cache Management",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Free local application storage cache",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                ShadcnButton(
                    onClick = {
                        val imageLoader = coil.Coil.imageLoader(context)
                        imageLoader.diskCache?.clear()
                        imageLoader.memoryCache?.clear()
                        Toast.makeText(context, "Image cache cleared!", Toast.LENGTH_SHORT).show()
                    },
                    variant = ShadcnButtonVariant.Outline,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Clear Image Cache")
                }
            }

            // --- STATS CARD ---
            ShadcnCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Gallery Statistics",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Items", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Text("$totalCount files", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Storage", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Text(formatSize(totalSize), fontWeight = FontWeight.Bold)
                }
            }

            // --- ABOUT CARD ---
            ShadcnCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "About App",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = "PixelVault Gallery v1.0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Designed with Shadcn UI & Compose components",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // --- UPDATE CHANNEL SELECTOR CARD ---
            ShadcnCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Update Channel",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Select Stable for tested features or Beta for experimental updates",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))

                ShadcnTabSwitch(
                    options = listOf("Stable", "Beta"),
                    selectedIndex = if (updateChannel == "beta") 1 else 0,
                    onOptionSelected = { index ->
                        val newChannel = if (index == 1) "beta" else "stable"
                        onUpdateChannelChange(newChannel)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // --- UPDATE CHECKER INITIATOR ---
            ShadcnCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Software Updates",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Check for new release packages on GitHub",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(12.dp))
                
                ShadcnButton(
                    onClick = onCheckForUpdates,
                    variant = ShadcnButtonVariant.Primary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Check for Updates Now")
                }
            }
        }
    }
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
