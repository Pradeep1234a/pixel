package com.pradeep.pixelgrid.data

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val path: String,
    val size: Long,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val dateAdded: Long, // timestamp in seconds
    val duration: Long,  // in milliseconds (0 for images)
    val isFavorite: Boolean = false,
    val bucketName: String // Folder name (e.g. "Camera", "Download")
) {
    val isVideo: Boolean
        get() = mimeType.startsWith("video")
}

data class MediaBucket(
    val name: String,
    val coverUri: Uri,
    val itemCount: Int
)

data class RectBounds(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float
)
