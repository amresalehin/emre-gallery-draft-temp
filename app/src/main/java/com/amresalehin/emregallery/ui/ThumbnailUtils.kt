package com.amresalehin.emregallery.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import kotlin.math.max

/**
 * Common thumbnail and image caching utilities for Emre Gallery.
 * Unifies cache keys across all screens (Home, Albums, Favourites, Bin, Archive, Search)
 * so thumbnails are cached once, shared instantly across screens, and used as instantaneous
 * zero-latency placeholders when expanding the fullscreen media viewer.
 */
object ThumbnailUtils {
    const val MICRO_THUMBNAIL_DIMENSION = 140
    const val THUMBNAIL_DIMENSION = 280

    fun mediaThumbnailKey(uri: String): String = "${uri}_thumb"
    fun mediaMicroThumbnailKey(uri: String): String = "${uri}_thumb_micro"

    fun buildThumbnailRequest(
        context: Context,
        uri: String,
        widthPx: Int = THUMBNAIL_DIMENSION,
        heightPx: Int = THUMBNAIL_DIMENSION,
        useRgb565: Boolean = false
    ): ImageRequest {
        val builder = ImageRequest.Builder(context)
            .data(Uri.parse(uri))
            .size(widthPx, heightPx)
            .precision(Precision.INEXACT)
            // Progressive placeholder from in-memory micro progressive thumbnail
            .placeholderMemoryCacheKey(mediaMicroThumbnailKey(uri))
            // Smooth 100ms crossfade preserves continuous retinal tracking and eliminates pop-in judder
            .crossfade(true)
            .crossfade(100)
            .diskCacheKey(mediaThumbnailKey(uri))
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey(mediaThumbnailKey(uri))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)

        if (useRgb565) {
            builder.bitmapConfig(Bitmap.Config.RGB_565)
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            builder.bitmapConfig(Bitmap.Config.HARDWARE)
        }
        return builder.build()
    }

    fun buildMicroThumbnailRequest(
        context: Context,
        uri: String
    ): ImageRequest {
        return ImageRequest.Builder(context)
            .data(Uri.parse(uri))
            .size(MICRO_THUMBNAIL_DIMENSION, MICRO_THUMBNAIL_DIMENSION)
            .precision(Precision.INEXACT)
            .crossfade(true)
            .crossfade(80)
            .diskCacheKey(mediaMicroThumbnailKey(uri))
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCacheKey(mediaMicroThumbnailKey(uri))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .allowRgb565(true)
            .build()
    }

    fun buildViewerRequest(
        context: Context,
        uri: String,
        isVideo: Boolean
    ): ImageRequest {
        val builder = ImageRequest.Builder(context)
            .data(Uri.parse(uri))
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)

        if (isVideo) {
            builder
                .size(THUMBNAIL_DIMENSION, THUMBNAIL_DIMENSION)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .crossfade(120)
                .placeholderMemoryCacheKey(mediaMicroThumbnailKey(uri))
                .diskCacheKey(mediaThumbnailKey(uri))
                .memoryCacheKey(mediaThumbnailKey(uri))
        } else {
            val dm = context.resources.displayMetrics
            val maxDim = max(dm.widthPixels, dm.heightPixels).coerceIn(1600, 2560)
            builder
                .size(maxDim, maxDim)
                .precision(Precision.INEXACT)
                // When expanding full viewer, immediately use grid thumbnail as zero-latency placeholder
                .placeholderMemoryCacheKey(mediaThumbnailKey(uri))
                .diskCacheKey("${uri}:viewer-scaled")
                .memoryCacheKey("${uri}:viewer-scaled")
                .crossfade(true)
                .crossfade(150)
        }
        return builder.build()
    }
}
