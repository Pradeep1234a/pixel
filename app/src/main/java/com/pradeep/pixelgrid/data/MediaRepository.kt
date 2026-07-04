package com.pradeep.pixelgrid.data

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaRepository {

    private const val PREFS_NAME = "pixelvault_prefs"
    private const val KEY_FAVORITES = "favorite_media_ids"

    // Get list of favorited IDs
    fun getFavoriteIds(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_FAVORITES, emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
    }

    // Toggle favorite state
    fun toggleFavorite(context: Context, id: Long): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val favorites = getFavoriteIds(context).toMutableSet()
        val isNowFavorite = if (favorites.contains(id)) {
            favorites.remove(id)
            false
        } else {
            favorites.add(id)
            true
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites.map { it.toString() }.toSet()).apply()
        return isNowFavorite
    }

    // Query both images and videos from MediaStore and sort by dateAdded descending
    suspend fun fetchMediaList(context: Context): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()
        val favoriteIds = getFavoriteIds(context)

        // 1. Query Images
        val imageProjection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            imageProjection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                val name = cursor.getString(nameColumn) ?: ""
                val path = cursor.getString(dataColumn) ?: ""
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeColumn) ?: "image/jpeg"
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val bucketName = cursor.getString(bucketColumn) ?: "Photos"

                mediaList.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        name = name,
                        path = path,
                        size = size,
                        mimeType = mimeType,
                        width = width,
                        height = height,
                        dateAdded = dateAdded,
                        duration = 0,
                        isFavorite = favoriteIds.contains(id),
                        bucketName = bucketName
                    )
                )
            }
        }

        // 2. Query Videos
        val videoProjection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            videoProjection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
            val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                val name = cursor.getString(nameColumn) ?: ""
                val path = cursor.getString(dataColumn) ?: ""
                val size = cursor.getLong(sizeColumn)
                val mimeType = cursor.getString(mimeColumn) ?: "video/mp4"
                val width = cursor.getInt(widthColumn)
                val height = cursor.getInt(heightColumn)
                val dateAdded = cursor.getLong(dateColumn)
                val duration = cursor.getLong(durationColumn)
                val bucketName = cursor.getString(bucketColumn) ?: "Videos"

                mediaList.add(
                    MediaItem(
                        id = id,
                        uri = uri,
                        name = name,
                        path = path,
                        size = size,
                        mimeType = mimeType,
                        width = width,
                        height = height,
                        dateAdded = dateAdded,
                        duration = duration,
                        isFavorite = favoriteIds.contains(id),
                        bucketName = bucketName
                    )
                )
            }
        }

        // Sort both together descending by dateAdded
        mediaList.sortedByDescending { it.dateAdded }
    }

    // Query buckets (albums)
    suspend fun fetchMediaBuckets(context: Context): List<MediaBucket> = withContext(Dispatchers.IO) {
        val mediaList = fetchMediaList(context)
        mediaList.groupBy { it.bucketName }
            .map { (bucketName, items) ->
                MediaBucket(
                    name = bucketName,
                    coverUri = items.first().uri,
                    itemCount = items.size
                )
            }.sortedByDescending { it.itemCount }
    }

    // Delete a media item. Returns an IntentSender on Android 10+ if user authorization is required.
    suspend fun deleteMediaItem(context: Context, item: MediaItem): IntentSender? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.delete(item.uri, null, null)
            null
        } catch (securityException: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val recoverableSecurityException = securityException as? RecoverableSecurityException
                    ?: throw securityException
                recoverableSecurityException.userAction.actionIntent.intentSender
            } else {
                throw securityException
            }
        }
    }
}
