package com.amresalehin.emregallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

class MediaRepository(private val resolver: ContentResolver) {

    fun loadMedia(): List<MediaItem> {
        val rows = ArrayList<MediaItem>()
        queryCollection(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, rows)
        queryCollection(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, rows)
        return rows.sortedWith(compareByDescending<MediaItem> {
            if (it.dateTakenMillis > 0) it.dateTakenMillis else it.dateAddedSeconds * 1000L
        }.thenBy { it.id })
    }

    private fun queryCollection(collection: Uri, video: Boolean, rows: MutableList<MediaItem>) {
        val projectionList = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.MIME_TYPE)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.SIZE)
            add(MediaStore.MediaColumns.WIDTH)
            add(MediaStore.MediaColumns.HEIGHT)
            add(MediaStore.MediaColumns.RELATIVE_PATH)
            add(MediaStore.MediaColumns.DATE_TAKEN)
            if (video) {
                add(MediaStore.Video.VideoColumns.DURATION)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                add(MediaStore.MediaColumns.IS_TRASHED)
            }
        }
        val projection = projectionList.toTypedArray()
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val queryArgs = android.os.Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE)
                putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
            }
            runCatching { resolver.query(collection, projection, queryArgs, null) }.getOrNull()
                ?: resolver.query(collection, projection, null, null, sort)
        } else {
            resolver.query(collection, projection, null, null, sort)
        }

        cursor?.use { c ->
            val idCol = c.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val mimeCol = c.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
            val addedCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val sizeCol = c.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val widthCol = c.getColumnIndex(MediaStore.MediaColumns.WIDTH)
            val heightCol = c.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
            val pathCol = c.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val takenCol = c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
            val durationCol = if (video) c.getColumnIndex(MediaStore.Video.VideoColumns.DURATION) else -1
            val trashedCol = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                c.getColumnIndex(MediaStore.MediaColumns.IS_TRASHED)
            } else -1

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val itemUri = ContentUris.withAppendedId(collection, id)
                val name = c.getString(nameCol) ?: "Untitled"
                val mimeType = if (mimeCol >= 0) c.getString(mimeCol) else null
                val added = if (addedCol >= 0) c.getLong(addedCol) else 0L
                val taken = if (takenCol >= 0 && !c.isNull(takenCol)) c.getLong(takenCol) else 0L
                val size = if (sizeCol >= 0) c.getLong(sizeCol) else 0L
                val width = if (widthCol >= 0) c.getInt(widthCol) else 0
                val height = if (heightCol >= 0) c.getInt(heightCol) else 0
                val path = if (pathCol >= 0) c.getString(pathCol) ?: "" else ""
                val duration = if (durationCol >= 0 && !c.isNull(durationCol)) c.getLong(durationCol) else 0L
                val isTrashed = if (trashedCol >= 0 && !c.isNull(trashedCol)) c.getInt(trashedCol) != 0 else false

                rows.add(
                    MediaItem(
                        id = id,
                        uri = itemUri.toString(),
                        name = name,
                        mimeType = mimeType,
                        dateAddedSeconds = added,
                        dateTakenMillis = taken,
                        sizeBytes = size,
                        width = width,
                        height = height,
                        relativePath = path,
                        isVideo = video,
                        durationMillis = duration,
                        isTrashed = isTrashed
                    )
                )
            }
        }
    }
}
